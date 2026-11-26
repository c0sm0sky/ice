#!/usr/bin/env python3
"""Browser-test the migrated PrimeFaces version of the ICE compatibility fixture."""

import argparse
import pathlib
import sys
from urllib.parse import parse_qs, urljoin, urlsplit
from compatibility_contract import ACTION_SELECTORS

try:
    from playwright.sync_api import sync_playwright
except ImportError:
    print("Install Playwright and Chromium: python -m pip install playwright && python -m playwright install chromium",
          file=sys.stderr)
    raise SystemExit(2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:8180/prime/")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    args = parser.parse_args()
    base_url = args.url.rstrip("/") + "/"
    failures = []

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page()
        page.set_default_timeout(10_000)
        page_errors = []
        page.on("pageerror", lambda error: page_errors.append(str(error)))

        def check(condition, label, details=""):
            if condition:
                print(f"PASS {label}")
            else:
                failures.append(label)
                print(f"FAIL {label}: {details or 'assertion failed'}", file=sys.stderr)

        def ajax(label, locator, expected_text=None):
            try:
                source = locator.get_attribute("id")
            except Exception as error:
                raise RuntimeError(f"{label}: action element is unavailable") from error

            def is_partial(response):
                request = response.request
                if request.method != "POST" or request.headers.get("faces-request", "").lower() != "partial/ajax":
                    return False
                fields = parse_qs(request.post_data or "")
                return fields.get("jakarta.faces.source", fields.get("javax.faces.source", [])) == [source]

            with page.expect_response(is_partial, timeout=10_000) as response_info:
                locator.click()
            response = response_info.value
            try:
                body = response.text()
                body_error = ""
            except Exception as exception:
                body = ""
                body_error = str(exception)
            page.wait_for_load_state("domcontentloaded")
            if expected_text is not None:
                page.wait_for_function(
                    "text => document.body.innerText.includes(text)", arg=expected_text, timeout=10_000
                )
            visible = page.locator("body").inner_text()
            ok = response.status == 200 and "xml" in response.headers.get("content-type", "").lower()
            body_is_partial = "<partial-response" in body
            body_unavailable_after_navigation = "navigated away from" in body_error
            ok = ok and (body_is_partial or body_unavailable_after_navigation)
            if expected_text is not None:
                ok = ok and expected_text in visible
            ok = ok and "Error processing request" not in visible and "Server Internal Error" not in visible
            details = (f"HTTP {response.status}; type={response.headers.get('content-type')}; "
                       f"request={response.request.url}; response={response.url}; "
                       f"body={body[:180]!r}; body_error={body_error!r}; page={visible[:180]!r}")
            check(ok, label, details)
            current = urlsplit(page.url)
            check(current.path.rstrip("/") == urlsplit(base_url).path.rstrip("/")
                  and not current.query and not current.fragment,
                  label + " keeps clean application URL", page.url)

        def sort_ajax(label, header):
            table_id = header.get_attribute("id").rsplit(":", 1)[0]

            def is_sort_response(response):
                request = response.request
                if request.method != "POST" or request.headers.get("faces-request", "").lower() != "partial/ajax":
                    return False
                fields = parse_qs(request.post_data or "")
                return fields.get("jakarta.faces.source", fields.get("javax.faces.source", [])) == [table_id] \
                    and fields.get(table_id + "_sorting") == ["true"]

            with page.expect_response(is_sort_response, timeout=10_000) as response_info:
                header.click()
            response = response_info.value
            body = response.text()
            check(response.status == 200 and "<partial-response" in body,
                  label, f"HTTP {response.status}; {body[:220]!r}")
            page.wait_for_timeout(250)

        def check_link_style(selector, label):
            link = page.locator(selector).first
            styled = link.count() == 1 and link.evaluate("""e => {
                const s = getComputedStyle(e);
                return s.color === 'rgb(255, 255, 255)' && s.backgroundImage.includes('linear-gradient');
            }""")
            check(styled, label + " has shared button style")

        def select_report_owner(locator, label):
            target = locator.locator("option").filter(has_text=label).get_attribute("value")
            if locator.input_value() == target:
                return
            source = locator.get_attribute("id").replace("_input", "")

            def owner_change(response):
                request = response.request
                if request.method != "POST" or request.headers.get("faces-request", "").lower() != "partial/ajax":
                    return False
                fields = parse_qs(request.post_data or "")
                return fields.get("jakarta.faces.source", fields.get("javax.faces.source", [])) == [source]

            with page.expect_response(owner_change, timeout=10_000):
                locator.select_option(value=target)

        def check_navigation_layout():
            for selector, title in ((ACTION_SELECTORS["open_coverage"], "Komponenty"),
                                    (ACTION_SELECTORS["open_report"], "Dodatkowy flow")):
                link = page.locator(selector)
                group = link.locator("xpath=ancestor::div[contains(concat(' ', normalize-space(@class), ' '), ' navigation-group ')]")
                heading = group.locator("h2")
                ok = (group.count() == 1 and heading.count() == 1 and heading.inner_text() == title
                      and link.bounding_box()["y"] >= heading.bounding_box()["y"] + heading.bounding_box()["height"])
                check(ok, title + " link below matching heading")
            check(page.evaluate(r"""() => [...document.styleSheets].some(s => s.href && /ice\.css(?:[?]|$)/.test(s.href))
                  && getComputedStyle(document.body).fontFamily.includes('Inter')"""),
                  "shared stylesheet and typography applied")

        def check_flow_style(label):
            card = page.locator(".flow-card")
            styled = card.count() == 1 and card.evaluate(
                "e => { const s = getComputedStyle(e); return s.backgroundColor === 'rgb(255, 255, 255)' "
                "&& s.borderRadius === '12px' && s.boxShadow !== 'none' && parseFloat(s.padding) >= 20; }"
            )
            check(styled, label, "flow card has no expected shared-card styles")

        def open_secondary_flow():
            ajax("launch report flow AJAX", page.locator(ACTION_SELECTORS["open_report"]), "Run report")

        def goto_coverage():
            if page.locator("#mainForm\\:loginField").count():
                return
            if not page.locator(ACTION_SELECTORS["open_coverage"]).count():
                page.goto(base_url, wait_until="domcontentloaded")
            ajax("open compatibility panel AJAX", page.locator(ACTION_SELECTORS["open_coverage"]),
                 "Pola formularza")

        try:
            response = page.goto(base_url, wait_until="domcontentloaded")
            check(response.status == 200 and "Zaloguj się" in page.locator("body").inner_text(),
                  "login page loads")
            assets = page.locator("link[href], script[src]").evaluate_all(
                "nodes => nodes.map(n => n.href || n.src).filter(u => u.startsWith(location.origin))"
            )
            for asset in dict.fromkeys(assets):
                check(page.request.get(asset).status == 200, f"asset loads: {asset}")

            check_link_style(ACTION_SELECTORS["login"], "login link")
            page.locator(ACTION_SELECTORS["login_user"]).fill("invalid-user")
            page.locator(ACTION_SELECTORS["login_password"]).fill("wrong-password")
            ajax("invalid login AJAX", page.locator(ACTION_SELECTORS["login"]), "Nieprawidłowy login lub hasło.")
            page.locator(ACTION_SELECTORS["login_user"]).fill(args.username)
            page.locator(ACTION_SELECTORS["login_password"]).fill(args.password)
            ajax("valid login AJAX", page.locator(ACTION_SELECTORS["login"]), "Użytkownicy")
            check(page.url == base_url, "main URL stays at application root", page.url)

            check_navigation_layout()
            check_link_style(ACTION_SELECTORS["open_coverage"], "components link")
            check_link_style(ACTION_SELECTORS["open_report"], "report link")
            check_link_style(ACTION_SELECTORS["edit_user"], "edit link")
            check_link_style(ACTION_SELECTORS["logout"], "logout link")

            ajax("edit AJAX", page.locator(ACTION_SELECTORS["edit_user"]).first, "Edycja użytkownika")
            ajax("cancel AJAX", page.locator(ACTION_SELECTORS["cancel_user_edit"]), "Użytkownicy")
            ajax("reopen edit AJAX", page.locator(ACTION_SELECTORS["edit_user"]).first, "Edycja użytkownika")
            page.locator(ACTION_SELECTORS["edit_user_password"]).fill(args.password)
            ajax("save AJAX", page.locator(ACTION_SELECTORS["save_user"]), "Użytkownicy")
            ajax("logout AJAX", page.locator(ACTION_SELECTORS["logout"]), "Zaloguj się")
            page.locator(ACTION_SELECTORS["login_user"]).fill(args.username)
            page.locator(ACTION_SELECTORS["login_password"]).fill(args.password)
            ajax("login after logout AJAX", page.locator(ACTION_SELECTORS["login"]), "Użytkownicy")
            page.reload(wait_until="domcontentloaded")
            check("Użytkownicy" in page.locator("body").inner_text(), "authenticated refresh keeps session")
            table_box = page.locator(".user-table").bounding_box()
            links_box = page.locator(".user-actions").bounding_box()
            check(table_box is not None and links_box is not None
                  and links_box["y"] >= table_box["y"] + table_box["height"],
                  "report and component links appear below the user table")
            check(page.locator("body").evaluate("e => getComputedStyle(e).backgroundColor")
                  == "rgb(245, 247, 251)", "application stylesheet is applied")

            goto_coverage()
            check(page.url == base_url, "component page stays in the current flow session", page.url)
            check(page.locator("#mainForm\\:loginField").count() > 0,
                  "coverage view renders in the current flow")
            check("Error processing request" not in page.locator("body").inner_text(),
                  "authenticated coverage link renders")
            check_link_style(ACTION_SELECTORS["coverage_back"], "back link")
            for label, selector in (
                ("text input", "#mainForm\\:loginField"),
                ("password input", "#mainForm\\:passwordField"),
                ("select menu", "#mainForm\\:roleField_input"),
                ("multi-select", "select[multiple]"),
                ("radio group", ".compatibility-choice input[type=radio]"),
                ("file upload", "#mainForm\\:fileField_input"),
                ("rich text editor", "#mainForm\\:richTextField_editor"),
                ("calendar", "#mainForm\\:dateField_input"),
                ("autocomplete", "#mainForm\\:suggestionField_input"),
                ("menu bar", ".ui-menubar"),
                ("context menu", "#mainForm\\:coverageContextMenu"),
                ("tab view", ".ui-tabs"),
                ("data table", ".compatibility-table"),
                ("progress bar", ".ui-progressbar"),
                ("confirmation dialog", "#mainForm\\:coverageConfirmation"),
                ("popup dialog", "#mainForm\\:coveragePopup"),
                ("tooltip", "#mainForm\\:coverageTooltip"),
                ("divider", ".compatibility-divider"),
            ):
                check(page.locator(selector).count() > 0, f"component renders: {label}")

            def table_logins():
                return [row.locator("td").nth(0).inner_text().strip()
                        for row in page.locator("div.compatibility-table tbody tr").all()]

            logins_before_sort = table_logins()
            sort_ajax("sort table by first name AJAX",
                      page.locator("div.compatibility-table th").filter(has_text="Imię").first)
            sorted_logins = table_logins()
            check(len(logins_before_sort) >= 3 and sorted_logins != logins_before_sort
                  and sorted_logins == ["admin", "bob", "alice"],
                  "table sorts rows by first name", repr(sorted_logins))

            page.locator("#mainForm\\:userFilter").fill("alice")
            ajax("filter table AJAX", page.locator(ACTION_SELECTORS["coverage_filter"]))
            check(table_logins() == ["alice"], "table filter keeps matching user")
            ajax("clear table filter AJAX", page.locator(ACTION_SELECTORS["coverage_clear_filter"]))
            check(len(table_logins()) >= 3, "clearing table filter restores rows")

            page.locator("#mainForm\\:loginField").fill("coverage-test")
            page.locator("#mainForm\\:passwordField").fill("coverage-secret")
            page.locator("#mainForm\\:roleField_input").select_option("user")
            page.locator("select[multiple]").select_option(["admin", "user"])
            page.locator('.compatibility-choice input[type="radio"][value="admin"]').check()
            check(page.locator("#mainForm\\:roleField_input").input_value() == "user"
                  and page.locator("select[multiple]").evaluate(
                      "e => Array.from(e.selectedOptions).map(o => o.value)") == ["admin", "user"]
                  and page.locator('.compatibility-choice input[type="radio"][value="admin"]').is_checked(),
                  "form controls accept selections")

            suggestion = page.locator("#mainForm\\:suggestionField_input")
            suggestion.fill("")
            suggestion.click()
            suggestion.press_sequentially("adm", delay=100)
            suggestion_rows = page.locator("#mainForm\\:suggestionField_panel .ui-autocomplete-item")
            try:
                suggestion_rows.first.wait_for(state="visible", timeout=2500)
                check(suggestion_rows.all_inner_texts() == ["admin", "administrator"],
                      "autocomplete filters matching suggestions")
                suggestion_rows.nth(1).click()
                check(suggestion.input_value() == "administrator", "autocomplete selects a suggestion")
            except Exception as error:
                check(False, "autocomplete filters and selects suggestions", str(error).splitlines()[0])

            editor = page.locator("#mainForm\\:richTextField_editor .ql-editor")
            editor.fill("PrimeFaces editor test")
            check("PrimeFaces editor test" in editor.inner_text(), "rich text editor accepts text")
            file_input = page.locator("#mainForm\\:fileField_input")
            file_input.set_input_files({"name": "primefaces-upload.txt", "mimeType": "text/plain",
                                        "buffer": b"PrimeFaces upload test"})
            check("primefaces-upload.txt" in page.locator("#mainForm\\:fileField").inner_text(),
                  "file upload queues a file")
            with page.expect_response(
                lambda response: response.request.method == "POST"
                and response.request.headers.get("faces-request", "").lower() == "partial/ajax"
                and response.url.startswith(base_url),
                timeout=10_000,
            ) as upload_response:
                page.locator("#mainForm\\:fileField button.ui-fileupload-upload").click()
            upload_result = upload_response.value
            check(upload_result.status == 200 and "<partial-response" in upload_result.text(),
                  "file upload AJAX completes", f"HTTP {upload_result.status}")

            date_input = page.locator("#mainForm\\:dateField_input")
            original_date = date_input.input_value()
            date_input.click()
            page.locator("#mainForm\\:dateField_panel .ui-datepicker-calendar a").filter(has_text="15").first.click()
            check(date_input.input_value() != original_date, "calendar selects a date")

            second_tab = page.locator(".ui-tabs-nav li").nth(1)
            second_tab.click()
            check("Druga zakładka ICEfaces" in page.locator("body").inner_text(), "tabs switch panels")

            confirm_link = page.locator(ACTION_SELECTORS["coverage_confirm"])
            confirm_link.click()
            confirm = page.locator("#mainForm\\:coverageConfirmation")
            page.wait_for_timeout(250)
            check(confirm.is_visible(), "confirmation opens")
            if confirm.is_visible():
                cancel = confirm.get_by_role("button", name="Nie")
                cancel.click()
                check(not confirm.is_visible(), "confirmation cancel closes")
                confirm_link.click()
                confirm.get_by_role("button", name="Tak").click()
                check(not confirm.is_visible(), "confirmation accept closes")

            ajax("open popup AJAX", page.locator(ACTION_SELECTORS["coverage_popup_open"]), "Okno ICEfaces")
            dialog = page.locator("#mainForm\\:coveragePopup")
            check(dialog.is_visible(), "popup opens")
            if dialog.is_visible():
                close_link = page.locator(ACTION_SELECTORS["coverage_popup_close"])
                if close_link.count():
                    ajax("close popup AJAX", close_link)
                    check(not dialog.is_visible(), "popup closes")
                else:
                    check(False, "popup exposes its AJAX close action", "converted dialog body has no close link")
                    close_button = dialog.locator(".ui-dialog-titlebar-close")
                    if close_button.count():
                        close_button.click()
                        check(not dialog.is_visible(), "popup closes with PrimeFaces titlebar control")

            tooltip_target = page.locator(".compatibility-tooltip-target")
            tooltip_target.hover()
            page.wait_for_timeout(650)
            tooltip_content = page.get_by_text("Informacje o użytkowniku", exact=True)
            check(tooltip_content.count() > 0 and tooltip_content.is_visible(), "tooltip appears on hover")
            page.mouse.move(5, 5)
            page.wait_for_timeout(150)

            context_target = page.locator(".compatibility-context-target")
            context_target.click(button="right")
            context_menu = page.locator("#mainForm\\:coverageContextMenu")
            check(context_menu.is_visible(), "context menu opens on right-click")
            if context_menu.is_visible():
                menu_link = page.locator(ACTION_SELECTORS["coverage_context_return"])
                if menu_link.count():
                    try:
                        ajax("context menu link returns to users AJAX", menu_link, "Użytkownicy")
                    except Exception:
                        check(False, "context menu link returns to users", page.url)
                        page.goto(base_url, wait_until="domcontentloaded")
                else:
                    check(False, "context menu contains return link")
                    page.goto(base_url, wait_until="domcontentloaded")
            else:
                goto_coverage()

            goto_coverage()
            try:
                menu_return = page.locator(ACTION_SELECTORS["coverage_menu_return"])
                ajax("menu link returns to users AJAX", menu_return, "Użytkownicy")
            except Exception as error:
                check(False, "menu link returns to users", str(error).splitlines()[0])
                page.goto(base_url, wait_until="domcontentloaded")
            users_view = (page.locator(ACTION_SELECTORS["open_report"]).count() == 1
                          and page.locator(ACTION_SELECTORS["open_coverage"]).count() == 1
                          and page.locator(ACTION_SELECTORS["coverage_back"]).count() == 0)
            check(users_view, "menu return renders the users view")
            if users_view:
                page.reload(wait_until="domcontentloaded")
                users_links_after_refresh = (page.locator(ACTION_SELECTORS["open_report"]).count() == 1
                                             and page.locator(ACTION_SELECTORS["open_coverage"]).count() == 1)
                check(users_links_after_refresh, "users links survive refresh after menu return")
                ajax("report link opens after menu return", page.locator(ACTION_SELECTORS["open_report"]),
                     "Run report")
                ajax("report flow returns after menu return", page.locator(ACTION_SELECTORS["cancel_report"]),
                     "Użytkownicy")
                ajax("components link works after menu return", page.locator(ACTION_SELECTORS["open_coverage"]),
                     "Pola formularza")
                ajax("menu return works after report flow", page.locator(ACTION_SELECTORS["coverage_menu_return"]),
                     "Użytkownicy")
            goto_coverage()
            divider = page.locator(".compatibility-divider")
            divider.scroll_into_view_if_needed()
            first_pane = page.locator(".compatibility-divider-pane").first
            if first_pane.count():
                divider_box = divider.bounding_box()
                original_width = first_pane.evaluate(
                    "e => e.style.flexBasis || getComputedStyle(e).flexBasis"
                )
                page.mouse.move(divider_box["x"] + divider_box["width"] / 2,
                                divider_box["y"] + divider_box["height"] / 2)
                page.mouse.down()
                page.mouse.move(divider_box["x"] + 70, divider_box["y"] + divider_box["height"] / 2, steps=6)
                page.mouse.up()
                check(first_pane.evaluate(
                    "e => (e.style.flexBasis || getComputedStyle(e).flexBasis)"
                ) != original_width, "divider can be resized")
            else:
                check(False, "divider has two resizable panes", "converted PrimeFaces divider has no pane markup")

            goto_coverage()
            page.keyboard.press("Escape")
            ajax("coverage back link returns to users AJAX", page.locator(ACTION_SELECTORS["coverage_back"]),
                 "Użytkownicy")
            try:
                page.wait_for_timeout(250)
                check("Użytkownicy" in page.locator("body").inner_text(), "coverage back link returns to users")
            except Exception:
                check(False, "coverage back link returns to users", page.url)
                page.goto(base_url, wait_until="domcontentloaded")

            open_secondary_flow()
            check_flow_style("report flow is styled")
            check_link_style(ACTION_SELECTORS["run_report"], "run report link")
            check_link_style(ACTION_SELECTORS["save_note"], "save note link")
            owner_choice = page.locator("select").nth(0)
            report_choice = page.locator("select").nth(1)
            note_draft = page.locator('textarea[id$=":noteDraft"]')
            draft_text = "Niezapisany szkic podczas zmiany właściciela"
            note_draft.fill(draft_text)
            previous_report_values = report_choice.locator("option").evaluate_all(
                "options => options.map(option => option.value)"
            )
            select_report_owner(owner_choice, "alice")
            page.wait_for_function("""previous => {
                const select = document.querySelector('select[id$=\\":reportChoice_input\\"]');
                return select && [...select.options].map(option => option.value).join('|') !== previous.join('|');
            }""", arg=previous_report_values)
            check(note_draft.input_value() == draft_text,
                  "owner AJAX preserves unsaved textarea draft")
            refreshed_report_values = report_choice.locator("option").evaluate_all(
                "options => options.map(option => option.value)"
            )
            check(len(refreshed_report_values) > 1
                  and refreshed_report_values != previous_report_values
                  and report_choice.input_value() in refreshed_report_values,
                  "owner AJAX refreshes and selects that owner's reports",
                  f"values={refreshed_report_values}, selected={report_choice.input_value()}")
            page.wait_for_function("() => [...document.querySelectorAll('.flow-status')].some(e => e.textContent.trim() === 'Właściciel: alice')")
            check(page.locator(".flow-status").filter(has_text="Właściciel: alice").count() == 1,
                  "owner label updates to alice")
            select_report_owner(owner_choice, "bob")
            page.wait_for_function("() => [...document.querySelectorAll('.flow-status')].some(e => e.textContent.trim() === 'Właściciel: bob')")
            check(page.locator(".flow-status").filter(has_text="Właściciel: bob").count() == 1,
                  "owner label updates to bob")
            select_report_owner(owner_choice, "admin — Admin Systemu")
            page.wait_for_function("() => [...document.querySelectorAll('.flow-status')].some(e => e.textContent.trim() === 'Właściciel: admin')")
            check(page.locator(".flow-status").filter(has_text="Właściciel: admin").count() == 1,
                  "owner label updates to admin")
            check(note_draft.input_value() == draft_text,
                  "second owner AJAX preserves unsaved textarea draft")
            note_text = "Notatka zapisana przez flow"
            note_draft.fill(note_text)
            ajax("save note through flow AJAX", page.locator(ACTION_SELECTORS["save_note"]), note_text)
            check(note_text in page.locator("body").inner_text(), "saved note remains after flow action")
            unsaved_note = "Niezapisana zmiana notatki"
            note_draft.fill(unsaved_note)
            ajax("run report AJAX", page.locator(ACTION_SELECTORS["run_report"]), "Raport użytkowników")
            check_flow_style("report result is styled")
            check_link_style(ACTION_SELECTORS["download_report"], "download report link")
            check_link_style(ACTION_SELECTORS["finish_report"], "finish report link")
            report_table = page.locator(".flow-report-table")
            report_text = report_table.inner_text()
            check(report_table.locator("tbody tr").count() > 0 and "admin" in report_text
                  and note_text in page.locator("body").inner_text(), "report displays database rows and note")
            check(unsaved_note not in page.locator("body").inner_text(),
                  "running report does not save note draft")
            with page.expect_download(timeout=10_000) as download_info:
                page.locator(ACTION_SELECTORS["download_report"]).click()
            download = download_info.value
            csv_content = pathlib.Path(download.path()).read_text(encoding="utf-8-sig")
            check(download.suggested_filename == "raport-uzytkownikow.csv", "report download has filename")
            check('"admin"' in csv_content and "Admin" in csv_content, "downloaded CSV contains report data")
            check(f'Notatka,"{note_text}"' in csv_content and unsaved_note not in csv_content,
                  "downloaded CSV contains saved note instead of draft")
            ajax("return to report selection AJAX", page.locator(ACTION_SELECTORS["back_to_report_list"]), "Run report")
            check("Dostępne raporty" in page.locator("body").inner_text()
                  and "admin" in page.locator("body").inner_text().lower(),
                  "report selection returns with database entries and owner")
            owner_choice = page.locator("select").nth(0)
            select_report_owner(owner_choice, "admin — Admin Systemu")
            report_choice = page.locator("select").nth(1)
            report_choice.select_option(label="Profil użytkownika")
            ajax("run user profile AJAX", page.locator(ACTION_SELECTORS["run_report"]), "Rekordy raportu")
            check(page.locator(".flow-report-table tbody tr").count() == 1
                  and "admin" in page.locator(".flow-report-table").inner_text(),
                  "profile report shows its owner's row")
            with page.expect_download(timeout=10_000) as profile_download:
                page.locator(ACTION_SELECTORS["download_report"]).click()
            profile_csv = pathlib.Path(profile_download.value.path()).read_text(encoding="utf-8-sig")
            profile_headers = [header.inner_text().strip() for header in page.locator(".flow-report-table thead th").all()]
            check('"admin"' in profile_csv and "Admin" in profile_csv
                  and f'Notatka,"{note_text}"' in profile_csv and ",".join(profile_headers) in profile_csv
                  and "Notatka" not in profile_headers,
                  "profile CSV matches table headers, owner data and note")
            ajax("return from profile to report selection AJAX", page.locator(ACTION_SELECTORS["back_to_report_list"]),
                 "Run report")
            select_report_owner(page.locator("select").nth(0), "admin — Admin Systemu")
            page.locator("select").nth(1).select_option(label="Raporty użytkownika")
            ajax("run user's reports AJAX", page.locator(ACTION_SELECTORS["run_report"]),
                 "Raporty przypisane użytkownikowi: admin")
            assigned_rows = page.locator(".flow-report-table tbody tr")
            check(assigned_rows.count() == 2 and "Profil użytkownika" in assigned_rows.nth(0).inner_text(),
                  "user report shows only reports assigned to that user")
            with page.expect_download(timeout=10_000) as assigned_download:
                page.locator(ACTION_SELECTORS["download_report"]).click()
            assigned_csv = pathlib.Path(assigned_download.value.path()).read_text(encoding="utf-8-sig")
            assigned_headers = [header.inner_text().strip() for header in page.locator(".flow-report-table thead th").all()]
            check("Profil użytkownika" in assigned_csv and "Raporty użytkownika" in assigned_csv
                  and f'Notatka,"{note_text}"' in assigned_csv
                  and ",".join(assigned_headers) in assigned_csv and "Notatka" not in assigned_headers,
                  "assigned-report CSV matches table headers, rows and note")
            ajax("return from user reports to report selection AJAX", page.locator(ACTION_SELECTORS["back_to_report_list"]),
                 "Run report")
            select_report_owner(page.locator("select").nth(0), "admin — Admin Systemu")
            page.locator("select").nth(1).select_option(label="Profil użytkownika")
            ajax("reopen report from report list AJAX", page.locator(ACTION_SELECTORS["run_report"]), "Raport użytkowników")
            ajax("finish report AJAX", page.locator(ACTION_SELECTORS["finish_report"]), "Użytkownicy")

            page.goto(base_url, wait_until="domcontentloaded")
            open_secondary_flow()
            check(note_text in page.locator("body").inner_text(), "saved note survives new flow in session")
            ajax("report exception AJAX", page.locator(ACTION_SELECTORS["fail_report"]), "Raport niedostępny")
            check_flow_style("report error is styled")
            ajax("return from report error", page.locator(ACTION_SELECTORS["return_from_report_error"]), "Użytkownicy")

            page.goto(base_url, wait_until="domcontentloaded")
            open_secondary_flow()
            ajax("unavailable report skip AJAX", page.locator(ACTION_SELECTORS["skip_report"]),
                 "Użytkownicy")
            page.goto(base_url, wait_until="domcontentloaded")
            open_secondary_flow()
            ajax("cancel report AJAX", page.locator(ACTION_SELECTORS["cancel_report"]), "Użytkownicy")
            check(not page_errors, "browser has no JavaScript errors", "; ".join(page_errors))
        except Exception as error:
            failures.append("test execution")
            print(f"FAIL test execution: {type(error).__name__}: {error}", file=sys.stderr)
        finally:
            browser.close()

    if failures:
        print(f"FAILED {len(failures)} check(s): {', '.join(failures)}", file=sys.stderr)
        return 1
    print("PASS all PrimeFaces compatibility AJAX checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
