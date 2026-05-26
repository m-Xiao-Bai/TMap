import { ref } from 'vue'
import { useSystemConfig } from '@/composables/useSystemConfig'

/**
 * 语音输入（Web Speech API 优先）
 */
export function useVoiceInput() {
  const transcribing = ref(false)
  const { getConfigValue } = useSystemConfig()

  let recognition = null
  let silenceTimer = null
  let maxTimer = null

  function isSupported() {
    return !!(window.SpeechRecognition || window.webkitSpeechRecognition)
  }

  async function start({ onPartial, onFinal, onSilence } = {}) {
    if (!navigator.mediaDevices) {
      throw new Error('NO_MIC')
    }

    if (!isSupported()) {
      throw new Error('NOT_SUPPORTED')
    }

    const SR = window.SpeechRecognition || window.webkitSpeechRecognition
    recognition = new SR()
    recognition.lang = 'zh-CN'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.maxAlternatives = 1

    const silenceMs = parseInt(getConfigValue('agent.voice.silence_ms') ?? '1500')
    const maxMs = parseInt(getConfigValue('agent.voice.max_duration_ms') ?? '60000')

    let finalText = ''

    recognition.onresult = (e) => {
      let interim = ''
      let finalSegment = ''
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const t = e.results[i][0].transcript
        if (e.results[i].isFinal) finalSegment += t
        else interim += t
      }
      if (finalSegment) finalText += finalSegment
      const visible = finalText + interim
      onPartial?.(visible)
      if (finalSegment) onFinal?.(finalText)

      if (silenceTimer) clearTimeout(silenceTimer)
      silenceTimer = setTimeout(() => {
        onSilence?.()
        stop()
      }, silenceMs)
    }

    recognition.onerror = (e) => {
      console.warn('SR error', e.error)
      stop()
    }

    recognition.onend = () => {
      transcribing.value = false
    }

    try {
      recognition.start()
      transcribing.value = true
    } catch (e) {
      throw new Error('START_FAILED:' + e.message)
    }

    // 最长录音
    maxTimer = setTimeout(() => stop(), maxMs)
  }

  function stop() {
    if (recognition) {
      try { recognition.stop() } catch {}
      recognition = null
    }
    if (silenceTimer) { clearTimeout(silenceTimer); silenceTimer = null }
    if (maxTimer) { clearTimeout(maxTimer); maxTimer = null }
    transcribing.value = false
  }

  return { start, stop, transcribing, isSupported }
}
