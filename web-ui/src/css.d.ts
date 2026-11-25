// Webpack's style-loader/css-loader handle these side-effect imports at build time;
// TypeScript 6 checks side-effect imports by default, so it needs a declaration.
declare module '*.css';
