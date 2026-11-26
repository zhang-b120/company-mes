<template>
  <div class="tvm-card">
    <div class="tvm-ct">
      <div class="tvm-ct-l">{{ title }}</div>
      <span v-if="tag" :class="['tvm-tag', tagCls]">{{ tag }}</span>
    </div>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  title: String,
  tag: String,
  tagCls: { type: String, default: '' }
})
</script>
