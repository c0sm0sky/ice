#!/usr/bin/env python3
"""Exercise partial-submit draft restoration and changed server-side options."""

from pathlib import Path

from playwright.sync_api import sync_playwright


def main():
    script = (Path(__file__).resolve().parents[1] /
              "rewrite-plugins/src/main/resources/primefaces-partial-submit.js").read_text()
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page()
        page.set_content('''<form id="f"><select id="owner"><option>A</option><option>B</option></select>
          <textarea id="note"></textarea><input id="text"><input id="password" type="password">
          <input id="date" type="date"><input id="check" type="checkbox">
          <select id="unchanged"><option>A</option><option>B</option></select>
          <select id="dependent"><option>A</option><option>B</option></select></form>''')
        page.add_script_tag(content=script)
        page.locator("#note").fill("draft")
        page.locator("#text").fill("text")
        page.locator("#password").fill("secret")
        page.locator("#date").fill("2026-05-15")
        page.locator("#check").check()
        page.locator("#unchanged").select_option(label="B")
        page.locator("#dependent").select_option(label="B")
        page.evaluate('''() => {
          PrimeFacesPartialSubmit.capture('owner');
          const form = document.getElementById('f');
          form.innerHTML = `<select id="owner"><option>A</option><option selected>B</option></select>
            <textarea id="note"></textarea><input id="text"><input id="password" type="password">
            <input id="date" type="date"><input id="check" type="checkbox">
            <select id="unchanged"><option>A</option><option>B</option></select>
            <select id="dependent"><option selected>C</option><option>D</option></select>`;
          PrimeFacesPartialSubmit.restore();
        }''')
        assert page.locator("#note").input_value() == "draft"
        assert page.locator("#text").input_value() == "text"
        assert page.locator("#password").input_value() == "secret"
        assert page.locator("#date").input_value() == "2026-05-15"
        assert page.locator("#check").is_checked()
        assert page.locator("#unchanged").input_value() == "B"
        assert page.locator("#dependent").input_value() == "C"
        assert page.locator("#owner").input_value() == "B"
        browser.close()
    print("PASS draft fields preserved and changed server options respected")


if __name__ == "__main__":
    main()
