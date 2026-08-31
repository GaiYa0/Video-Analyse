<template>
    <header class="header-section">
      <div class="title-container">
        <i class="ph-upload-bold"></i>
        <h1>算法上传</h1>
      </div>
      <progress-bar :progress="progress" />
    </header>
  </template>
  
  <script>
  import ProgressBar from './ProgressBar.vue'
  
  export default {
    name: 'HeaderSection',
    
    components: {
      ProgressBar
    },
  
    props: {
      progress: {
        type: Number,
        required: true
      }
    }
  }
  </script>
  
  <style scoped>
  .header-section {
    background-color: white;
    padding: 1.5rem;
    border-radius: 8px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  }
  
  .title-container {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 1rem;
  }
  
  .title-container i {
    font-size: 24px;
  }
  
  h1 {
    font-size: 1.5rem;
    font-weight: 600;
    color: #333;
    margin: 0;
  }
  </style>