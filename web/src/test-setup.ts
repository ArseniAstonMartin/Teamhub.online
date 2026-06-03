import '@testing-library/jest-dom';

// Polyfill Pointer Capture APIs missing from jsdom (needed by Radix UI)
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => undefined;
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => undefined;
}

// Polyfill scrollIntoView (missing from jsdom, used by Radix UI Select)
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => undefined;
}
