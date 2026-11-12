export type CachedSiteConfig = {
  tutorialDocUrlStudent: string
  tutorialDocUrlPublisher: string
  tutorialBannerText: string
}

const KEY = 'whu.siteConfig.v1'

export function readCachedSiteConfig(): CachedSiteConfig | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<CachedSiteConfig>
    return {
      tutorialDocUrlStudent: parsed.tutorialDocUrlStudent || '',
      tutorialDocUrlPublisher: parsed.tutorialDocUrlPublisher || '',
      tutorialBannerText: parsed.tutorialBannerText || '查看使用教程',
    }
  } catch {
    return null
  }
}

export function writeCachedSiteConfig(config: CachedSiteConfig) {
  try {
    localStorage.setItem(KEY, JSON.stringify(config))
  } catch {
    /* ignore quota / private mode */
  }
}
