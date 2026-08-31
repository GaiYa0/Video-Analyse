<template>
    <div class="progress-container">
      <div class="progress-bar" :style="{ width: progress + '%' }" />
    </div>
  </template>
  
  <script>
  export default {
    name: 'ProgressBar',
    
    props: {
      progress: {
        type: Number,
        required: true
      }
    }
  }
  </script>
  
  <style scoped>
  .progress-container {
    width: 100%;
    height: 8px;
    background-color: #eee;
    border-radius: 4px;
    overflow: hidden;
  }
  
  .progress-bar {
    height: 100%;
    background-color: #2196f3;
    transition: width 0.3s ease;
  }
  </style>