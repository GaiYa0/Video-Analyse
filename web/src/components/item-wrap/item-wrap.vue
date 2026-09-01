<template>
  <div class="lr_titles">
    <div class="item_title" v-if="title !== ''">
      <div class="title-inner" :style="{ left : titleOffsetX, top: titleOffsetY }">{{ title }}</div>
    </div>
    <div :class="title !== '' ? 'item_title_content' : 'item_title_content_def'">
      <slot></slot>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {};
  },
  props: {
    title: {
      type: String,
      default: () => "",
    },
    titleOffsetX: {
      type: String,
      required: false
    },
    titleOffsetY: {
      type: String,
      required: false
    },
  },
};
</script>

<style lang='scss' scoped>
$item-title-height: 40px;
$item_title_content-height: calc(100% - 40px);

.lr_titles {
  margin: 8px;
  box-sizing: border-box;
  border: 1px solid var(--sva-border);
  border-radius: var(--sva-radius);
  background: var(--sva-surface);
  overflow: hidden;

  :deep(.border-box-content) {
    box-sizing: border-box;
    padding: 6px 16px 0px;
  }

  .item_title {
    height: $item-title-height;
    line-height: $item-title-height;
    width: 100%;
    color: var(--sva-text-muted);
    position: relative;
    border-bottom: 1px solid var(--sva-border);

    .title-inner {
      font-weight: 600;
      font-size: 14px;
      letter-spacing: 1px;
      color: var(--sva-text);
      position: absolute;
      left: 16px;
      top: 0;
    }
  }

  .item_title_content {
    height: $item_title_content-height;
  }

  .item_title_content_def {
    width: 100%;
    height: 100%;
  }
}
</style>
