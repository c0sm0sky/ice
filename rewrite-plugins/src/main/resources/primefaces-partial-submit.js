(function () {
    'use strict';
    const dirty = new WeakSet();
    let snapshot = [];

    function track(event) {
        const field = event.target;
        if (field && field.matches && field.matches('input,textarea,select')) dirty.add(field);
    }
    document.addEventListener('input', track, true);
    document.addEventListener('change', track, true);

    function options(field) {
        return Array.from(field.options, option => [option.value, option.text, option.disabled]);
    }

    window.PrimeFacesPartialSubmit = {
        capture: function (sourceId) {
            snapshot = [];
            const source = document.getElementById(sourceId);
            const form = source && source.closest('form');
            if (!form) return;
            form.querySelectorAll('input,textarea,select').forEach(function (field) {
                if (!field.id || !dirty.has(field) || source.contains(field) || field.disabled ||
                    field.type === 'file' || field.type === 'hidden') return;
                const item = {id: field.id, type: field.type, value: field.value};
                if (field.type === 'checkbox' || field.type === 'radio') item.checked = field.checked;
                if (field.tagName === 'SELECT') {
                    item.options = JSON.stringify(options(field));
                    item.selected = Array.from(field.selectedOptions, option => option.value);
                }
                snapshot.push(item);
            });
        },
        restore: function () {
            snapshot.forEach(function (item) {
                const field = document.getElementById(item.id);
                if (!field || field.type !== item.type || field.disabled) return;
                if (field.tagName === 'SELECT') {
                    if (JSON.stringify(options(field)) !== item.options) return;
                    Array.from(field.options).forEach(option => { option.selected = item.selected.includes(option.value); });
                    const wrapper = field.closest('.ui-selectonemenu');
                    const label = wrapper && wrapper.querySelector('.ui-selectonemenu-label');
                    if (label && field.selectedOptions.length) label.textContent = field.selectedOptions[0].text;
                } else if (field.type === 'checkbox' || field.type === 'radio') {
                    field.checked = item.checked;
                    const wrapper = field.closest('.ui-chkbox,.ui-radiobutton');
                    const box = wrapper && wrapper.querySelector('.ui-chkbox-box,.ui-radiobutton-box');
                    if (box) {
                        box.classList.toggle('ui-state-active', item.checked);
                        box.setAttribute('aria-checked', String(item.checked));
                        const icon = box.querySelector('.ui-chkbox-icon,.ui-radiobutton-icon');
                        if (icon) icon.classList.toggle('ui-icon-check', item.checked);
                    }
                } else {
                    field.value = item.value;
                }
                dirty.add(field);
            });
            snapshot = [];
        },
        clear: function () { snapshot = []; }
    };
}());
