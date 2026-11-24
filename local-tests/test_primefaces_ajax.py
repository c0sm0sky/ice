#!/usr/bin/env python3
"""Exercise the migrated JSF/PrimeFaces AJAX flow against a running WildFly app."""

import argparse
import os
import re
import sys
from html.parser import HTMLParser
from http.cookiejar import CookieJar
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlsplit
from urllib.request import HTTPCookieProcessor, Request, build_opener
from xml.etree import ElementTree


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.view_state = None
        self.controls = []
        self.inputs = {}
        self.resources = []
        self.current_control = None

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if attributes.get("name") == "jakarta.faces.ViewState":
            self.view_state = attributes.get("value")
        if tag == "input" and attributes.get("name"):
            self.inputs[attributes["name"]] = attributes
        resource = attributes.get("href") if tag == "link" else attributes.get("src") if tag == "script" else None
        if resource:
            self.resources.append(resource)
        if tag in {"a", "button"}:
            self.current_control = [tag, attributes, ""]

    def handle_data(self, data):
        if self.current_control:
            self.current_control[2] += data

    def handle_endtag(self, tag):
        if self.current_control and self.current_control[0] == tag:
            self.controls.append(self.current_control)
            self.current_control = None


def parse_page(html):
    page = Page()
    page.feed(html)
    return page


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:8180/prime/", help="WAR context URL")
    parser.add_argument("--username", default=os.getenv("PRIME_TEST_USER", "admin"))
    parser.add_argument("--password", default=os.getenv("PRIME_TEST_PASSWORD", "admin"))
    args = parser.parse_args()
    base_url = args.url.rstrip("/") + "/"
    client = build_opener(HTTPCookieProcessor(CookieJar()))

    def request(url, data=None, headers=None):
        req = Request(url, data=data, headers=headers or {})
        return client.open(req, timeout=20)

    def assert_contains(text, expected, label):
        if expected not in text:
            raise AssertionError(f"{label}: expected {expected!r} in response")

    def click(page, label, extra=None, form=None, predicate=None):
        choices = [control for control in page.controls if control[2].strip() == label]
        if predicate:
            choices = [control for control in choices if predicate(control[1].get("onclick", ""))]
        if not choices:
            raise AssertionError(f"control not found: {label}")
        onclick = choices[0][1].get("onclick", "")
        match = re.search(r's:"([^"]+)"', onclick)
        if not match:
            raise AssertionError(f"AJAX source not found for control: {label}")
        source = match.group(1)
        fields = {
            "mainForm": "mainForm",
            "jakarta.faces.ViewState": page.view_state or "",
            "jakarta.faces.partial.ajax": "true",
            "jakarta.faces.source": source,
            "jakarta.faces.partial.execute": "mainForm",
            "jakarta.faces.partial.render": "mainForm",
            source: source,
        }
        fields.update(form or {})
        fields.update(extra or {})
        response = request(base_url, urlencode(fields).encode(), {
            "Faces-Request": "partial/ajax",
            "X-Requested-With": "XMLHttpRequest",
            "Accept": "application/xml, text/xml, */*; q=0.01",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        })
        xml = response.read().decode("utf-8", "replace")
        if response.status != 200:
            raise AssertionError(f"{label}: HTTP {response.status}")
        root = ElementTree.fromstring(xml)
        if root.tag != "partial-response":
            raise AssertionError(f"{label}: expected a Faces partial response")
        error = root.find("./error")
        if error is not None:
            raise AssertionError(f"{label}: Faces AJAX error: {''.join(error.itertext()).strip()}")
        redirect = root.find("./redirect")
        if redirect is not None:
            location = redirect.get("url")
            if not location:
                raise AssertionError(f"{label}: Faces redirect has no URL")
            target = urljoin(base_url, location)
            parsed_target = urlsplit(target)
            parsed_base = urlsplit(base_url)
            if ((parsed_target.scheme, parsed_target.netloc, parsed_target.path.rstrip("/")) !=
                    (parsed_base.scheme, parsed_base.netloc, parsed_base.path.rstrip("/"))
                    or parsed_target.query or parsed_target.fragment):
                raise AssertionError(f"{label}: Faces redirect leaves the application root: {target}")
            redirected = request(target)
            final_url = urlsplit(redirected.geturl())
            if ((final_url.scheme, final_url.netloc, final_url.path.rstrip("/")) !=
                    (parsed_base.scheme, parsed_base.netloc, parsed_base.path.rstrip("/"))
                    or final_url.query or final_url.fragment):
                raise AssertionError(f"{label}: redirect chain leaves the application root: {redirected.geturl()}")
            html = redirected.read().decode("utf-8", "replace")
            next_page = parse_page(html)
            if not next_page.view_state:
                raise AssertionError(f"{label}: redirected page has no Jakarta Faces ViewState")
            return xml + html, next_page
        updates = {node.get("id"): node.text or "" for node in root.findall(".//update")}
        html = updates.get("mainForm", "")
        if not html:
            raise AssertionError(f"{label}: partial response did not update mainForm")
        updated_state = next((value for key, value in updates.items() if key and "ViewState" in key), None)
        next_page = parse_page(html)
        next_page.view_state = updated_state or page.view_state
        return xml, next_page

    try:
        response = request(base_url)
        if response.status != 200:
            raise AssertionError(f"login page: HTTP {response.status}")
        login_html = response.read().decode("utf-8", "replace")
        page = parse_page(login_html)
        if not page.view_state:
            raise AssertionError("login page has no Jakarta Faces ViewState")
        print(f"login page: HTTP 200 ({base_url})")

        asset_statuses = []
        for source in dict.fromkeys(page.resources):
            asset_url = urljoin(base_url, source)
            asset = request(asset_url, headers={"Accept": "*/*"})
            content_type = asset.headers.get("Content-Type", "")
            body = asset.read()
            ok = asset.status == 200 and bool(body) and not content_type.startswith("text/html")
            asset_statuses.append(ok)
            if not ok:
                raise AssertionError(f"resource failed: HTTP {asset.status} {content_type} {asset_url}")
        print(f"PrimeFaces/CSS resources: {sum(asset_statuses)}/{len(asset_statuses)} HTTP 200")

        xml, page = click(page, "Zaloguj się", form={
            "mainForm:user": args.username,
            "mainForm:pass": args.password,
        })
        assert_contains(xml, "Użytkownicy", "login AJAX")
        print("PASS login AJAX")

        def is_current_user(onclick):
            match = re.search(r'name:"userLogin",value:"([^"]+)"', onclick)
            return match and match.group(1) == args.username

        xml, page = click(page, "Edycja", extra={"userLogin": args.username}, predicate=is_current_user)
        assert_contains(xml, "Edycja użytkownika", "edit AJAX")
        if "mainForm:password" not in page.inputs:
            raise AssertionError("edit form has no password field")
        print("PASS edit AJAX")

        xml, page = click(page, "Anuluj")
        assert_contains(xml, "Użytkownicy", "cancel AJAX")
        print("PASS cancel AJAX")

        xml, page = click(page, "Edycja", extra={"userLogin": args.username}, predicate=is_current_user)
        assert_contains(xml, "Edycja użytkownika", "reopen edit AJAX")
        xml, page = click(page, "Zapisz", form={"mainForm:password": args.password})
        assert_contains(xml, "Użytkownicy", "save AJAX")
        print("PASS save AJAX (writes the existing password back unchanged)")

        xml, page = click(page, "Wyloguj")
        assert_contains(xml, "Zaloguj się", "logout AJAX")
        print("PASS logout AJAX")

        xml, page = click(page, "Zaloguj się", form={
            "mainForm:user": args.username,
            "mainForm:pass": args.password,
        })
        assert_contains(xml, "Użytkownicy", "login again AJAX")
        refreshed = request(base_url).read().decode("utf-8", "replace")
        assert_contains(refreshed, "Użytkownicy", "authenticated refresh")
        assert_contains(refreshed, args.username, "authenticated refresh")
        print("PASS relogin AJAX and authenticated refresh on the same URL")
        print("PASS all migrated AJAX checks")
    except (AssertionError, HTTPError, URLError, ElementTree.ParseError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
