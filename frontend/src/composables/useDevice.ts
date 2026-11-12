import { computed, inject, onMounted, onUnmounted, provide, ref, type InjectionKey, type Ref } from 'vue'

export const IS_MOBILE_KEY: InjectionKey<Ref<boolean>> = Symbol('isMobile')

const MOBILE_QUERY = '(max-width: 768px)'

/** Root-only: create reactive device state and provide it. */
export function provideDevice() {
  // Sync on create so the first paint (and plaza list mode) is not stuck on desktop.
  const isMobile = ref(
    typeof window !== 'undefined' ? window.matchMedia(MOBILE_QUERY).matches : false,
  )
  let media: MediaQueryList | null = null

  function sync() {
    if (typeof window === 'undefined') {
      isMobile.value = false
      return
    }
    const narrow = window.matchMedia(MOBILE_QUERY).matches
    // Width is the primary signal; coarse pointer is a weak hint for edge cases only.
    isMobile.value = narrow
  }

  onMounted(() => {
    media = window.matchMedia(MOBILE_QUERY)
    sync()
    media.addEventListener('change', sync)
    // 微信内置浏览器会盖住底部 fixed 栏，打标给 CSS 补安全间距
    if (/MicroMessenger/i.test(navigator.userAgent)) {
      document.documentElement.classList.add('is-wechat')
    }
  })

  onUnmounted(() => {
    media?.removeEventListener('change', sync)
  })

  provide(IS_MOBILE_KEY, isMobile)
  return { isMobile: computed(() => isMobile.value) }
}

/** Child views: inject mobile flag from App shell. */
export function useIsMobile() {
  const injected = inject(IS_MOBILE_KEY, null)
  if (injected) return computed(() => injected.value)
  // Fallback if used outside App (e.g. tests): local media query.
  const local = ref(typeof window !== 'undefined' && window.matchMedia(MOBILE_QUERY).matches)
  return computed(() => local.value)
}
