# PART 3 — JAVASCRIPT

This folder contains simple, internship-ready JavaScript programs with explanations and key viva concepts.

---

## 7. Group Words by Their First Character Using `Map`

* **File**: [`groupWordsByFirstChar.js`](./groupWordsByFirstChar.js)

### Logic
Iterates through words, extracts `word[0].toLowerCase()`, initializes an empty array in the `Map` if the character doesn't exist, and pushes the word into that group.

### How to Run
```bash
node groupWordsByFirstChar.js
```

---

## 8. Dynamic Light/Dark Theme Switcher

* **Directory**: [`themeSwitcher/`](./themeSwitcher/)
  * [`index.html`](./themeSwitcher/index.html)
  * [`style.css`](./themeSwitcher/style.css)
  * [`script.js`](./themeSwitcher/script.js)

### How It Works
Toggles the `.dark` class on `document.body` when the button is clicked, updating background and text colors via CSS and updating the button text label.

---

## 9. Custom HTML Component Using Native Web Components

* **Directory**: [`webComponent/`](./webComponent/)
  * [`index.html`](./webComponent/index.html)
  * [`script.js`](./webComponent/script.js)

### Key Viva Concepts
1. **`HTMLElement`**: The base class that all HTML elements inherit from.
2. **`customElements.define('employee-card', EmployeeCard)`**: Registers the custom tag with the browser.
3. **`this.attachShadow({ mode: 'open' })`**: Encapsulates markup and styling inside a Shadow DOM.
4. **`this.getAttribute('name')`**: Reads attributes passed to `<employee-card>`.
