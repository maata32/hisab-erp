export type SupportedLocale = 'fr' | 'ar' | 'en';

export interface LocaleDescriptor {
  code: SupportedLocale;
  label: string;
  rtl: boolean;
  flag: string;
}

export const SUPPORTED_LOCALES: LocaleDescriptor[] = [
  { code: 'fr', label: 'Français', rtl: false, flag: '🇫🇷' },
  { code: 'ar', label: 'العربية', rtl: true, flag: '🇲🇷' },
  { code: 'en', label: 'English', rtl: false, flag: '🇬🇧' },
];
