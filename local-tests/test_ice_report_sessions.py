#!/usr/bin/env python3
"""Verify that report actions retain the correct bean across concurrent HTTP sessions."""
from playwright.sync_api import sync_playwright
from compatibility_contract import ACTION_SELECTORS as S


def run():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        contexts = [browser.new_context(), browser.new_context()]
        pages = [context.new_page() for context in contexts]
        try:
            for page in pages:
                page.goto('http://127.0.0.1:8080/ice/', wait_until='domcontentloaded')
                page.locator(S['login_user']).fill('admin')
                page.locator(S['login_password']).fill('admin')
                page.locator(S['login']).click()
                page.locator(S['open_report']).wait_for()
                page.locator(S['open_report']).click()
                page.locator('textarea[id$=":noteDraft"]').wait_for()
            for page, note in zip(pages, ('Notatka sesji A', 'Notatka sesji B')):
                page.locator('textarea[id$=":noteDraft"]').fill(note)
                page.locator(S['save_note']).click()
                page.get_by_text('Notatka zapisana: ' + note, exact=True).wait_for(timeout=10000)
            for page, note, other in ((pages[0], 'Notatka sesji A', 'Notatka sesji B'),
                                      (pages[1], 'Notatka sesji B', 'Notatka sesji A')):
                page.locator(S['run_report']).click()
                page.get_by_text('Raport użytkowników', exact=True).wait_for(timeout=10000)
                text = page.locator('body').inner_text()
                assert note in text and other not in text, text
                assert 'admin' in page.locator('.flow-report-table').inner_text()
            print('PASS separate concurrent report sessions')
        finally:
            for context in contexts:
                context.close()
            browser.close()


if __name__ == '__main__':
    run()
