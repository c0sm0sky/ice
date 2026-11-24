#!/usr/bin/env python3
"""Check that reloading the application root restores the current report and component views."""
import argparse
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright
from compatibility_contract import ACTION_SELECTORS as S


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True)
    args = parser.parse_args()
    base = args.url.rstrip('/') + '/'
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.set_default_timeout(10000)
        try:
            def reload_view(selector, label):
                page.reload(wait_until='domcontentloaded')
                page.locator(selector).first.wait_for(state='visible')
                current, expected = urlsplit(page.url), urlsplit(base)
                assert current.path.rstrip('/') == expected.path.rstrip('/') and not current.query and not current.fragment, page.url
                print('PASS ' + label, flush=True)

            page.goto(base, wait_until='domcontentloaded')
            page.locator(S['login_user']).fill('admin')
            page.locator(S['login_password']).fill('admin')
            page.locator(S['login']).click()
            page.locator(S['open_report']).wait_for()
            page.locator(S['open_coverage']).click()
            page.locator(S['coverage_back']).wait_for()
            reload_view(S['coverage_back'], 'component view survives refresh')
            page.locator(S['coverage_back']).click()
            page.locator(S['open_report']).wait_for()
            page.locator(S['open_report']).click()
            page.locator(S['save_note']).wait_for()
            page.locator('textarea[id$=":noteDraft"]').fill('Notatka po odświeżeniu')
            page.locator(S['save_note']).click()
            page.get_by_text('Notatka zapisana: Notatka po odświeżeniu', exact=True).wait_for()
            reload_view(S['run_report'], 'report form survives refresh')
            assert 'Notatka po odświeżeniu' in page.locator('body').inner_text()
            page.locator(S['run_report']).click()
            page.locator(S['finish_report']).wait_for()
            reload_view(S['finish_report'], 'report result survives refresh')
            assert 'admin' in page.locator('.flow-report-table').inner_text()
            assert 'Notatka po odświeżeniu' in page.locator('body').inner_text()
            page.locator(S['finish_report']).click()
            page.locator(S['open_report']).wait_for()
            page.locator(S['open_report']).click()
            page.locator(S['fail_report']).wait_for()
            page.locator(S['fail_report']).click()
            page.locator(S['return_from_report_error']).wait_for()
            reload_view(S['return_from_report_error'], 'report error survives refresh')
            page.locator(S['return_from_report_error']).click()
            page.locator(S['open_report']).wait_for()
            print('PASS flow actions after refresh', flush=True)
        finally:
            browser.close()


if __name__ == '__main__':
    main()
