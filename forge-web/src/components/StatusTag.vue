<template>
  <el-tag :type="map[status]?.type || 'info'" size="small">{{ map[status]?.label || status }}</el-tag>
</template>

<script setup lang="ts">
defineProps<{ status: string }>()
const map: Record<string, any> = {
  PENDING: { label: '排队中', type: 'info' },
  RUNNING: { label: '进行中', type: 'warning' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}
</script>
