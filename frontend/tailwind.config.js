/** @type {import('tailwindcss').Config} */
const { join } = require('path');

module.exports = {
  content: [
    join(__dirname, 'apps/**/!(*.stories|*.spec).{ts,html}'),
    join(__dirname, 'libs/**/!(*.stories|*.spec).{ts,html}'),
  ],
  theme: {
    extend: {
      screens: {
        sm: '640px',
        md: '768px',
        lg: '1024px',
        xl: '1280px',
        '2xl': '1536px',
      },
      colors: {
        // Hisab ERP brand teal (anchored on --hs-green #0E6E5A at 600, #0B5546 at 700)
        primary: {
          50: '#e7f5f1',
          100: '#c4e6dd',
          200: '#9bd5c7',
          300: '#69bda9',
          400: '#3a9e88',
          500: '#15846d',
          600: '#0E6E5A',
          700: '#0B5546',
          800: '#0a4639',
          900: '#08382e',
        },
      },
      minHeight: {
        touch: '44px',
      },
      minWidth: {
        touch: '44px',
      },
    },
  },
  plugins: [],
};
