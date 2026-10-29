<template>
  <div>
    <div class="toolbar" style="justify-content: space-between">
      <span class="muted">历史自动重放 · 实时推送</span>
      <el-button size="small" :icon="Download" @click="download">下载完整日志</el-button>
    </div>
    <div class="term">
      <div class="term-head">
        <div class="dots"><i></i><i></i><i></i></div>
        <div class="term-title">{{ kind === 'build' ? 'build' : 'deploy' }} · {{ id.slice(0, 8) }}</div>
        <el-input v-model="query" size="small" class="search" placeholder="搜索日志…" clearable @keyup.enter="nextMatch">
          <template #append><span class="match-count" @click="nextMatch">{{ matchCount ? `${matchIdx + 1}/${matchCount}` : '0' }}</span></template>
        </el-input>
        <el-tooltip content="回到底部" placement="top">
          <el-button size="small" text class="tool-btn" @click="scrollBottom"><el-icon><Bottom /></el-icon></el-button>
        </el-tooltip>
        <el-switch v-model="autoScroll" size="small" active-text="跟随" />
        <span class="s-tag" :class="ended ? 's-tag--info' : 's-tag--running'">
          <i class="dot"></i>{{ ended ? '已结束' : '实时推送中' }}
        </span>
      </div>
      <div ref="box" class="term-body"><span
        v-for="(l, i) in lines" :key="i" :data-i="i" :class="lineClass(l)"
        :style="isMatch(i) ? 'background: rgba(250,204,21,.25); display:inline-block; width:100%' : ''"
      >{{ l + '\n' }}</span><span v-if="running" class="term-cursor">▌</span></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { Download, Bottom } from '@element-plus/icons-vue'
import { fetchTicket } from '../api/api'

const props = defineProps<{ kind: 'build' | 'deploy'; id: string; logUrl?: string }>()
const lines = ref<string[]>([])
const ended = ref(false)
const connected = ref(false)
const box = ref<HTMLElement>()
const autoScroll = ref(true)
const query = ref('')
const matchIdx = ref(0)

const matchList = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return [] as number[]
  const out: number[] = []
  lines.value.forEach((l, i) => { if (l.toLowerCase().includes(q)) out.push(i) })
  return out
})
const matchCount = computed(() => matchList.value.length)

const isMatch = (i: number) => {
  const q = query.value.trim().toLowerCase()
  return !!q && lines.value[i]?.toLowerCase().includes(q)
}

let ws: WebSocket | null = null
let closedByEnd = false
let disposed = false

const running = computed(() => connected.value && !ended.value)

function lineClass(l: string) {
  if (l.startsWith('[forge] 构建成功') || l.startsWith('[forge] 部署成功')) return 'ok'
  if (l.startsWith('[forge] 构建失败') || l.startsWith('[forge] 部署失败')) return 'err'
  if (l.startsWith('[forge]') || l.startsWith('__FORGE')) return 'sys'
  return ''
}

function scrollBottom() {
  nextTick(() => { if (box.value) box.value.scrollTop = box.value.scrollHeight })
}

function nextMatch() {
  if (!matchCount.value) return
  matchIdx.value = (matchIdx.value + 1) % matchCount.value
  const target = matchList.value[matchIdx.value]
  nextTick(() => {
    const el = box.value?.querySelector(`[data-i="${target}"]`)
    el?.scrollIntoView({ block: 'center' })
  })
}

watch(matchCount, () => { matchIdx.value = 0 })

function append(line: string) {
  if (line === '__FORGE_LOG_END__') {
    ended.value = true
    closedByEnd = true
    ws?.close()
    return
  }
  lines.value.push(line)
  if (lines.value.length > 5000) lines.value.splice(0, 1000) // 防超长内存膨胀
  if (autoScroll.value) scrollBottom()
}

async function connect() {
  if (disposed) return
  // 每次连接先换取一次性 ticket，长效 JWT 不再进 URL（避免落入 access log）
  let ticket: string
  try {
    ticket = await fetchTicket()
  } catch {
    if (!disposed) setTimeout(connect, 2000)
    return
  }
  if (disposed) return
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/logs/${props.kind}/${props.id}?ticket=${encodeURIComponent(ticket)}`)
  ws.onopen = () => (connected.value = true)
  ws.onmessage = e => append(String(e.data))
  ws.onclose = () => {
    connected.value = false
    if (!closedByEnd && !ended.value && !disposed) {
      setTimeout(() => { if (!ended.value && !disposed) connect() }, 2000)
    }
  }
  ws.onerror = () => ws?.close()
}

async function download() {
  if (!props.logUrl) return
  const ticket = await fetchTicket()
  window.open(`${props.logUrl}?ticket=${encodeURIComponent(ticket)}`, '_blank')
}

onMounted(connect)
onUnmounted(() => {
  disposed = true
  ws?.close()
})
</script>

<style scoped>
.term-cursor { animation: blink 1s infinite; color: #7ec8e3; }
@keyframes blink { 50% { opacity: 0; } }
.search { width: 190px; }
.search :deep(.el-input__wrapper) { background: #0d1330; box-shadow: 0 0 0 1px rgba(124, 134, 255, .3) inset; --el-input-text-color: #a8b7d4; }
.match-count { cursor: pointer; font-size: 11px; color: #8b95b5; }
.tool-btn { color: #7d89a6; }
</style>
