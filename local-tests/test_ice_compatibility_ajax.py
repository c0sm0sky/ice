#!/usr/bin/env python3
"""Browser-test ICEfaces AJAX actions against a running WildFly fixture."""

import argparse
import pathlib
import sys
import uuid
from urllib.parse import parse_qs, urljoin, urlsplit
from compatibility_contract import ACTION_SELECTORS

try:
    from playwright.sync_api import sync_playwright
except ImportError:
    print("Install Playwright and Chromium: python -m pip install playwright && python -m playwright install chromium", file=sys.stderr)
    raise SystemExit(2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:8080/ice/")
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
            control_id = locator.get_attribute("id")

            def is_action_response(response):
                if response.request.method != "POST" or "/block/send-receive-updates" not in response.url:
                    return False
                fields = parse_qs(response.request.post_data or "")
                event_targets = fields.get("ice.event.target", [])
                accepted_targets = [control_id]
                if control_id and control_id.endswith(":link"):
                    accepted_targets.append(control_id[:-5] + ":out")
                return any(target in accepted_targets for target in event_targets)

            with page.expect_response(
                is_action_response,
                timeout=10_000,
            ) as response_info:
                locator.click()
            response = response_info.value
            page.wait_for_timeout(150)
            try:
                body = response.text()
            except Exception:
                # Flow end states can navigate to their configured view before Playwright reads the body.
                body = ""
            content_type = response.headers.get("content-type", "")
            visible = page.locator("body").inner_text()
            updates_received = "<updates>" in body or (not body and expected_text is not None
                                                       and expected_text in visible)
            ok = response.status == 200 and "text/xml" in content_type and updates_received
            if expected_text is not None:
                ok = ok and expected_text in visible
            ok = ok and "Conversion Error" not in visible and "Server Internal Error" not in visible
            check(ok, label, f"HTTP {response.status}, {content_type}; {visible[:220]!r}")
            current = urlsplit(page.url)
            check(current.path.rstrip("/") == urlsplit(base_url).path.rstrip("/")
                  and not current.query and not current.fragment,
                  label + " keeps clean application URL", page.url)

        def check_link_style(selector, label):
            link = page.locator(selector).first
            styled = link.count() == 1 and link.evaluate("""e => {
                const s = getComputedStyle(e);
                return s.color === 'rgb(255, 255, 255)' && s.backgroundImage.includes('linear-gradient');
            }""")
            check(styled, label + " has shared button style")

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

        def open_report(label="open report flow"):
            ajax(label, page.locator(ACTION_SELECTORS["open_report"]), "Run report")

        def goto_coverage():
            if page.locator("#mainForm\\:loginField").count():
                return
            ajax("open compatibility panel AJAX", page.locator(ACTION_SELECTORS["open_coverage"]),
                 "Pola formularza")
            check(urlsplit(page.url).path.rstrip("/") == urlsplit(base_url).path.rstrip("/"),
                  "component page stays in the current flow session", page.url)

        try:
            response = page.goto(base_url, wait_until="domcontentloaded")
            page.wait_for_timeout(300)
            check(response.status == 200 and "Zaloguj się" in page.locator("body").inner_text(),
                  "login page loads", f"HTTP {response.status}; {page.url}; {page.locator('body').inner_text()[:220]!r}")

            assets = page.locator("link[href], script[src]").evaluate_all(
                "nodes => nodes.map(n => n.href || n.src).filter(u => u.startsWith(location.origin))"
            )
            for asset in dict.fromkeys(assets):
                asset_response = page.request.get(asset)
                check(asset_response.status == 200, f"asset loads: {asset}")

            check_link_style(ACTION_SELECTORS["login"], "login link")
            page.locator(ACTION_SELECTORS["login_user"]).fill("invalid-user")
            page.locator(ACTION_SELECTORS["login_password"]).fill("wrong-password")
            ajax("invalid login", page.locator(ACTION_SELECTORS["login"]),
                        "Nieprawidłowy login lub hasło.")

            page.locator(ACTION_SELECTORS["login_user"]).fill(args.username)
            page.locator(ACTION_SELECTORS["login_password"]).fill(args.password)
            ajax("valid login", page.locator(ACTION_SELECTORS["login"]), "Użytkownicy")
            check(urlsplit(page.url).path.rstrip("/") == urlsplit(base_url).path.rstrip("/"),
                  "main URL stays at application root", page.url)

            check_navigation_layout()
            check_link_style(ACTION_SELECTORS["open_coverage"], "components link")
            check_link_style(ACTION_SELECTORS["open_report"], "report link")
            check_link_style(ACTION_SELECTORS["edit_user"], "edit link")
            check_link_style(ACTION_SELECTORS["logout"], "logout link")

            ajax("edit user", page.locator(ACTION_SELECTORS["edit_user"]).first, "Edycja użytkownika")
            ajax("cancel edit", page.locator(ACTION_SELECTORS["cancel_user_edit"]), "Użytkownicy")
            ajax("reopen edit", page.locator(ACTION_SELECTORS["edit_user"]).first, "Edycja użytkownika")
            page.locator(ACTION_SELECTORS["edit_user_password"]).fill(args.password)
            ajax("save user", page.locator(ACTION_SELECTORS["save_user"]), "Użytkownicy")
            ajax("logout", page.locator(ACTION_SELECTORS["logout"]), "Zaloguj się")

            page.locator(ACTION_SELECTORS["login_user"]).fill(args.username)
            page.locator(ACTION_SELECTORS["login_password"]).fill(args.password)
            ajax("login after logout", page.locator(ACTION_SELECTORS["login"]), "Użytkownicy")
            page.reload(wait_until="domcontentloaded")
            check("Użytkownicy" in page.locator("body").inner_text(), "authenticated refresh keeps session")

            goto_coverage()
            page.wait_for_timeout(250)
            check("Error processing request" not in page.locator("body").inner_text(),
                  "authenticated coverage link renders")
            check_link_style(ACTION_SELECTORS["coverage_back"], "back link")
            for label, selector in (
                ("text input", '[id="mainForm:loginField"]'),
                ("secret input", '[id="mainForm:passwordField"]'),
                ("select menu", '[id="mainForm:roleField"]'),
                ("multi-select", "select[multiple]"),
                ("radio group", 'input[type="radio"]'),
                ("file upload frame", 'iframe[id="mainForm:fileField:uploadFrame"]'),
                ("rich text editor", 'iframe[id="mainForm:richTextField___Frame"]'),
                ("calendar", "table.iceSelInpDate"),
                ("suggestion input", '[id="mainForm:suggestionField"]'),
                ("menu bar", ".iceMnuBar"),
                ("context menu", ".iceMnuPopVrt"),
                ("tab set", "table.icePnlTbSet"),
                ("data table", "table.iceDatTbl"),
                ("progress indicator", "table.iceOutProg"),
                ("confirmation panel", ".icePnlCnf"),
                ("popup", ".icePnlPop"),
                ("tooltip", ".icePnlTlTip"),
                ("divider", ".icePnlDvr"),
            ):
                check(page.locator(selector).count() > 0, f"component renders: {label}")

            def table_logins():
                return [row.locator("td").nth(0).inner_text().strip()
                        for row in page.locator("table.iceDatTbl tr[class*='iceDatTblRow']").all()]

            logins_before_sort = table_logins()
            ajax("sort table by first name AJAX",
                 page.locator("table.iceDatTbl a.iceCmdSrtHdr span").filter(has_text="Imię").first)
            sorted_logins = table_logins()
            check(len(logins_before_sort) >= 3 and sorted_logins != logins_before_sort
                  and sorted_logins == ["admin", "bob", "alice"],
                  "table sorts rows by first name", repr(sorted_logins))

            page.locator('[id="mainForm:userFilter"]').fill("alice")
            ajax("filter table AJAX", page.locator(ACTION_SELECTORS["coverage_filter"]))
            check(table_logins() == ["alice"], "table filter keeps matching user")
            ajax("clear table filter AJAX", page.locator(ACTION_SELECTORS["coverage_clear_filter"]))
            check(len(table_logins()) >= 3, "clearing table filter restores rows")

            page.locator('[id="mainForm:loginField"]').fill("coverage-test")
            page.locator('[id="mainForm:passwordField"]').fill("coverage-secret")
            page.locator('[id="mainForm:roleField"]').select_option("user")
            page.locator("select[multiple]").select_option(["admin", "user"])
            page.locator('input[type="radio"][value="admin"]').check()
            page.locator('[id="mainForm:suggestionField"]').fill("")
            check(page.locator('[id="mainForm:roleField"]').input_value() == "user"
                  and page.locator("select[multiple]").evaluate(
                      "e => Array.from(e.selectedOptions).map(o => o.value)") == ["admin", "user"]
                  and page.locator('input[type="radio"][value="admin"]').is_checked(),
                  "form controls accept selections")

            suggestion = page.locator('[id="mainForm:suggestionField"]')
            suggestion.click()
            suggestion.press_sequentially("adm", delay=100)
            suggestion_rows = page.locator("#mainForm\\:suggestionField_div .iceSelInpTxtRow")
            suggestion_rows.first.wait_for(state="visible")
            check(suggestion_rows.all_inner_texts() == ["admin", "administrator"],
                  "autocomplete filters matching suggestions")
            suggestion_rows.nth(1).click()
            check(suggestion.input_value() == "administrator", "autocomplete selects a suggestion")

            upload_frame = page.frame(name="mainForm:fileField:uploadFrame")
            upload_name = "icefaces-upload-" + uuid.uuid4().hex + ".txt"
            upload_frame.locator('input[type="file"]').set_input_files({
                "name": upload_name,
                "mimeType": "text/plain",
                "buffer": b"ICEfaces upload component check",
            })
            with page.expect_response(
                lambda response: response.request.method == "POST" and response.url.endswith("/uploadHtml"),
                timeout=10_000,
            ) as upload_response:
                upload_frame.locator('input[type="submit"]').click()
            check(upload_response.value.status == 200, "file upload accepts a file",
                  f"HTTP {upload_response.value.status}")

            editor = next(frame for frame in page.frames
                          if frame.parent_frame and "fckeditor.html" in frame.parent_frame.url
                          and frame.evaluate("() => document.designMode") == "on")
            editor.locator("body").click()
            page.keyboard.type("Editor test")
            check("Editor test" in editor.locator("body").inner_text(), "rich text editor accepts text")

            original_date = page.locator('[id="mainForm:dateField"]').input_value()
            page.locator('a[id^="mainForm:dateField_c_"]').filter(has_text="15").first.click()
            page.wait_for_timeout(150)
            check(page.locator('[id="mainForm:dateField"]').input_value() != original_date,
                  "calendar selects a date")
            page.locator("a.icePnlTbLblLnk").nth(1).click()
            page.wait_for_timeout(150)
            check("Druga zakładka ICEfaces" in page.locator("body").inner_text(), "tabs switch panels")

            page.locator(ACTION_SELECTORS["coverage_confirm"]).click()
            confirmation = page.locator(".icePnlCnf")
            check(confirmation.is_visible(), "confirmation opens")
            page.locator('input[id$="-cancel"]').click()
            page.wait_for_timeout(100)
            check(not confirmation.is_visible(), "confirmation cancel closes")
            page.locator(ACTION_SELECTORS["coverage_confirm"]).click()
            page.wait_for_timeout(100)
            page.locator('input[id$="-accept"]').click()
            page.wait_for_timeout(100)
            check(not confirmation.is_visible(), "confirmation accept closes")

            ajax("open popup AJAX", page.locator(ACTION_SELECTORS["coverage_popup_open"]), "Przykładowe okno ICEfaces")
            ajax("close popup AJAX", page.locator(ACTION_SELECTORS["coverage_popup_close"]))
            tooltip_target = page.locator(".compatibility-tooltip-target")
            tooltip_target.hover()
            page.wait_for_timeout(650)
            check("Informacje o użytkowniku" in page.locator(".icePnlTlTip").inner_text(),
                  "tooltip appears on hover")
            page.mouse.move(5, 5)
            page.wait_for_timeout(150)
            check(not page.locator(".icePnlTlTip").is_visible(), "tooltip hides on mouseout")

            context_target = page.locator(".compatibility-context-target")
            context_target.click(button="right")
            context_menu = page.locator(".iceMnuPopVrtTopSubMenu")
            check(context_menu.is_visible(), "context menu opens on right-click")
            page.locator(ACTION_SELECTORS["coverage_context_close"]).click()
            page.wait_for_timeout(100)
            check(not context_menu.is_visible(), "context menu closes")
            context_target.click(button="right")
            ajax("context menu link returns to users AJAX",
                 page.locator(ACTION_SELECTORS["coverage_context_return"]), "Użytkownicy")
            goto_coverage()

            divider = page.locator(".icePnlDvrSpt")
            divider.scroll_into_view_if_needed()
            divider_box = divider.bounding_box()
            first_pane = page.locator(".compatibility-dividerFst")
            original_width = first_pane.evaluate("element => element.style.width")
            page.mouse.move(divider_box["x"] + divider_box["width"] / 2,
                            divider_box["y"] + divider_box["height"] / 2)
            page.mouse.down()
            page.mouse.move(divider_box["x"] + 70, divider_box["y"] + divider_box["height"] / 2, steps=6)
            page.mouse.up()
            check(first_pane.evaluate("element => element.style.width") != original_width,
                  "divider can be resized")

            ajax("menu link returns to users AJAX",
                 page.locator(ACTION_SELECTORS["coverage_menu_return"]), "Użytkownicy")
            goto_coverage()
            ajax("coverage back link returns to users AJAX",
                 page.locator(ACTION_SELECTORS["coverage_back"]), "Użytkownicy")

            open_report()
            check_flow_style("report flow is styled")
            check_link_style(ACTION_SELECTORS["run_report"], "run report link")
            check_link_style(ACTION_SELECTORS["save_note"], "save note link")

            owner_choice = page.locator('select[id$=":reportOwner"]')
            for login in ("alice", "bob", "admin", "alice", "admin"):
                option = owner_choice.locator("option").filter(has_text=login).first
                owner_choice.select_option(value=option.get_attribute("value"))
                page.wait_for_function("login => [...document.querySelectorAll('.flow-status')].some(e => e.textContent.trim() === 'Właściciel: ' + login)", arg=login)
                check(page.locator(".flow-status").filter(has_text="Właściciel: " + login).count() == 1,
                      "ICE owner label updates to " + login)

            note_text = "Notatka zapisana przez flow"
            page.locator('textarea[id$=":noteDraft"]').fill(note_text)
            ajax("save note through flow", page.locator(ACTION_SELECTORS["save_note"]), note_text)
            check(note_text in page.locator("body").inner_text(), "saved note remains after flow action")
            unsaved_note = "Niezapisana zmiana notatki"
            page.locator('textarea[id$=":noteDraft"]').fill(unsaved_note)

            ajax("run report", page.locator(ACTION_SELECTORS["run_report"]), "Raport użytkowników")
            check_flow_style("report result is styled")
            check_link_style(ACTION_SELECTORS["download_report"], "download report link")
            check_link_style(ACTION_SELECTORS["finish_report"], "finish report link")
            report_rows = page.locator(".flow-report-table tbody tr")
            report_text = page.locator(".flow-report-table").inner_text()
            check(report_rows.count() > 0 and "admin" in report_text,
                  "report displays database users", repr(report_text[:300]))
            check(note_text in page.locator("body").inner_text(), "report displays saved note")
            check(unsaved_note not in page.locator("body").inner_text(),
                  "running report does not save note draft")
            with page.expect_download(timeout=10_000) as report_download:
                page.locator(ACTION_SELECTORS["download_report"]).click()
            download = report_download.value
            csv_content = pathlib.Path(download.path()).read_text(encoding="utf-8-sig")
            check(download.suggested_filename == "raport-uzytkownikow.csv", "ICEfaces report download has filename")
            check('"admin"' in csv_content and "Admin" in csv_content, "downloaded CSV contains report data")
            check(f'Notatka,"{note_text}"' in csv_content and unsaved_note not in csv_content,
                  "downloaded CSV contains saved note instead of draft")
            ajax("return to report selection", page.locator(ACTION_SELECTORS["back_to_report_list"]), "Run report")
            check("Dostępne raporty" in page.locator("body").inner_text()
                  and "admin" in page.locator("body").inner_text().lower(),
                  "report selection returns with database entries and owner")
            owner_choice = page.locator('select[id$=":reportOwner"]')
            owner_choice.select_option(label="admin — Admin Systemu")
            report_choice = page.locator('select[id$=":reportChoice"]')
            report_choice.select_option(label="Profil użytkownika")
            ajax("run user profile", page.locator(ACTION_SELECTORS["run_report"]), "Rekordy raportu")
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
            ajax("return from profile to report selection", page.locator(ACTION_SELECTORS["back_to_report_list"]), "Run report")
            page.locator('select[id$=":reportOwner"]').select_option(label="admin — Admin Systemu")
            report_choice = page.locator('select[id$=":reportChoice"]')
            report_choice.select_option(label="Raporty użytkownika")
            ajax("run user's reports", page.locator(ACTION_SELECTORS["run_report"]), "Raporty przypisane użytkownikowi: admin")
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
            ajax("return from user reports to report selection", page.locator(ACTION_SELECTORS["back_to_report_list"]), "Run report")
            page.locator('select[id$=":reportOwner"]').select_option(label="admin — Admin Systemu")
            page.locator('select[id$=":reportChoice"]').select_option(label="Profil użytkownika")
            ajax("reopen report from report list", page.locator(ACTION_SELECTORS["run_report"]), "Raport użytkowników")
            ajax("finish report", page.locator(ACTION_SELECTORS["finish_report"]),
                        "Użytkownicy")
            page.goto(base_url, wait_until="domcontentloaded")
            open_report("reopen report flow")
            reopened_text = page.locator("body").inner_text()
            check(note_text in reopened_text, "saved note survives a new flow execution in session")
            ajax("report exception", page.locator(ACTION_SELECTORS["fail_report"]),
                        "Raport niedostępny")
            check_flow_style("report error is styled")
            ajax("return from report error", page.locator(ACTION_SELECTORS["return_from_report_error"]),
                        "Użytkownicy")
            page.goto(base_url, wait_until="domcontentloaded")
            open_report("open flow for unavailable report branch")
            ajax("unavailable report decision returns to users",
                        page.locator(ACTION_SELECTORS["skip_report"]), "Użytkownicy")
            page.goto(base_url, wait_until="domcontentloaded")
            open_report("open flow for cancel branch")
            ajax("cancel report returns to users", page.locator(ACTION_SELECTORS["cancel_report"]),
                        "Użytkownicy")
            check(not page_errors, "browser has no JavaScript errors", "; ".join(page_errors))
        except Exception as error:
            failures.append("test execution")
            print(f"FAIL test execution: {type(error).__name__}: {error}", file=sys.stderr)
        finally:
            browser.close()

    if failures:
        print(f"FAILED {len(failures)} check(s): {', '.join(failures)}", file=sys.stderr)
        return 1
    print("PASS all ICEfaces AJAX checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
