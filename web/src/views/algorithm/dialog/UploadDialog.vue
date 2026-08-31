<template>
    <div>
      <transition name="fade">
        <div v-if="value" class="dialog-overlay" @click="handleClose">
          <div class="dialog-content" @click.stop>
            <button class="close-button" @click="handleClose">
              <i class="ph-x-bold"></i>
            </button>
            
            <div class="dialog-body">
              <header-section :progress="uploadState.progress" />
              <file-upload-section 
                @file-upload="handleUpload"
                @toggle-running="toggleRunning"
                :is-running="uploadState.isRunning"
              />
              <comment-section />
            </div>
          </div>
        </div>
      </transition>
    </div>
  </template>
  
  <script>
  import HeaderSection from './HeaderSection.vue'
  import FileUploadSection from './FileUploadSection.vue'
  import CommentSection from './CommentSection.vue'
  import {uploadImage} from '@/api/algorithm.js'
  export default {
    name: 'UploadDialog',
    
    components: {
      HeaderSection,
      FileUploadSection,
      CommentSection
    },
  
    props: {
      value: {
        type: Boolean,
        required: true
      }
    },
  
    data() {
      return {
        uploadState: {
          progress: 0,
          isRunning: false
        }
      }
    },
  
    methods: {
      handleClose() {
        this.$emit('input', false)
      },
  
      handleUpload(file) {
        let formData = new FormData();
        this.uploadState.progress = 0
        const interval = setInterval(() => {
          if (this.uploadState.progress < 100) {
            this.uploadState.progress += 10
          } else {
            clearInterval(interval)
          }
        }, 500)
      formData.append('file', file); // 将文件添加到 FormData 对象
      uploadImage(formData);
      },
  
      toggleRunning(value) {
        this.uploadState.isRunning = value
      }
    }
  }
  </script>
  
  <style scoped>
  .dialog-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.5);
    display: flex;
    justify-content: center;
    align-items: center;
    z-index: 1000;
  }
  
  .dialog-content {
    position: relative;
    width: 90%;
    max-width: 800px;
    max-height: 90vh;
    background-color: #f5f5f5;
    border-radius: 12px;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    overflow: hidden;
  }
  
  .dialog-body {
    padding: 2rem;
    overflow-y: auto;
    max-height: calc(90vh - 4rem);
    display: flex;
    flex-direction: column;
    gap: 2rem;
  }
  
  .close-button {
    position: absolute;
    top: 1rem;
    right: 1rem;
    background: none;
    border: none;
    cursor: pointer;
    padding: 0.5rem;
    color: #666;
    z-index: 1;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background-color 0.2s;
  }
  
  .close-button:hover {
    background-color: rgba(0, 0, 0, 0.05);
  }
  
  .fade-enter-active,
  .fade-leave-active {
    transition: opacity 0.3s ease;
  }
  
  .fade-enter,
  .fade-leave-to {
    opacity: 0;
  }
  
  @media (max-width: 768px) {
    .dialog-content {
      width: 95%;
    }
  
    .dialog-body {
      padding: 1rem;
    }
  }
  </style>