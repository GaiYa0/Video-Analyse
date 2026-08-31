<template>
    <div class="upload-section">
      <div class="upload-container">
        <label class="upload-label">
          <input 
            type="file" 
            class="file-input" 
            @change="handleFileChange"
            :disabled="isRunning"
          >
          <i class="ph-cloud-arrow-up-bold"></i>
          <span>点击上传文件</span>
        </label>
        
        <div class="actions">
          <button 
            class="action-button"
            :class="{ 'running': isRunning }"
            @click="handleRunClick"
          >
            <i class="ph-play-bold"></i>
            运行
          </button>
  
          <label class="stop-checkbox">
            <input 
              type="checkbox"
              :checked="isRunning"
              @change="handleStopChange"
            >
            <span>停止</span>
          </label>
        </div>
      </div>
    </div>
  </template>
  
  <script>
  export default {
    name: 'FileUploadSection',
  
    props: {
      isRunning: {
        type: Boolean,
        required: true
      }
    },
  
    methods: {
      handleFileChange(event) {
        const file = event.target.files[0]
        if (file) {
          this.$emit('file-upload', file)
        }
        // 重置input，允许重复上传相同文件
        event.target.value = ''
      },
  
      handleRunClick() {
        if (!this.isRunning) {
          this.$emit('toggle-running', true)
        }
      },
  
      handleStopChange(event) {
        this.$emit('toggle-running', event.target.checked)
      }
    }
  }
  </script>
  
  <style scoped>
  .upload-section {
    background-color: white;
    padding: 1.5rem;
    border-radius: 8px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  }
  
  .upload-container {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }
  
  .upload-label {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 2rem;
    border: 2px dashed #ccc;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.3s ease;
  }
  
  .upload-label:hover {
    border-color: #2196f3;
    background-color: rgba(33, 150, 243, 0.05);
  }
  
  .upload-label i {
    font-size: 2rem;
    color: #666;
    margin-bottom: 0.5rem;
  }
  
  .file-input {
    display: none;
  }
  
  .actions {
    display: flex;
    align-items: center;
    gap: 1rem;
  }
  
  .action-button {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    background-color: #2196f3;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    transition: background-color 0.3s ease;
  }
  
  .action-button:hover {
    background-color: #1976d2;
  }
  
  .action-button.running {
    background-color: #4caf50;
  }
  
  .action-button i {
    font-size: 1.2rem;
  }
  
  .stop-checkbox {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    cursor: pointer;
  }
  
  .stop-checkbox input {
    width: 16px;
    height: 16px;
  }
  
  .stop-checkbox span {
    color: #666;
  }
  
  @media (max-width: 768px) {
    .upload-label {
      padding: 1.5rem;
    }
  
    .actions {
      flex-direction: column;
      align-items: stretch;
    }
  
    .action-button {
      justify-content: center;
    }
  }
  </style>