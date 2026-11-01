# UI test readiness — 2 November 2026

Task branch codex/finance-ui-popup-readiness; parent e47bdae. No production source, dependency, app data or app APK change. Application SHA256 remains96b933a9960d4b42a5f7fb792f5f5b3753b1725edbec27372619ad0091f9924f.

API29 broader run was stopped after18 completed PASS/9 failures and a28th case in progress;69 cases were selected, not completed. Failures included modal-option lookup immediately after clicking, background buttons briefly unavailable in the merged tree, and keyboard dismissal using the main window token while a dialog owned focus. No unmerged-tree clicks or ignored assertions were introduced.

Fixture now resets portrait per case, waits for modal option appearance/disappearance, waits for real edit/logout/conflict controls in the normal merged tree, and uses Espresso closeSoftKeyboard against the focused window. No arbitrary sleep or application behavior changes. First focus6 run had3pass/3fail; second4pass/2fail exposed stale main-window IME-inset checking and a receipt-to-conflict rendering race. Final test APK assemble PASS; **6 PASS in151.998s**, `tmp/fin12-popup-focused3.txt`:200% font/landscape, attachment policy, season close/reopen, asset conflict, unchanged accepted harvest logout, latest incomplete asset logout. Earlier failed/interrupted logs are retained ignored, not treated as clean acceptance.

Full69 regression remains pending rerun. Backend108/1755, JVM59/lint0errors and FIN12 real API29/two reboot evidence remain applicable to unchanged application code.

API36 note: the earlier FIN12 reevaluation barrier did not complete within its deadline; cleanup force-stops an active owned instrumentation process. The resulting Process crashed message is not by itself an application crash proof. OS ANRs/provider delays were observed and no app Java fatal stack was found; full API36 reboot result remains unverified.
