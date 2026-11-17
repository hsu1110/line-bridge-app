import { onMounted, onUnmounted, type Ref } from 'vue'

/**
 * 確保彈窗開啟時按下 ESC 鍵必定關閉（若內部尚有開啟的下拉選單，則優先讓選單處理）
 */
export function useDialogEsc(visibles: Ref<boolean>[]) {
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Escape') return
    const openPopper = document.querySelector('.el-select-dropdown')
    if (openPopper && (openPopper as HTMLElement).offsetParent !== null) {
      return
    }
    for (let i = visibles.length - 1; i >= 0; i--) {
      if (visibles[i].value) {
        visibles[i].value = false
        break
      }
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeyDown))
  onUnmounted(() => window.removeEventListener('keydown', onKeyDown))
}
