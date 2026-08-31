<template>
    <div class="comment-section">
      <div class="comment-header">
        <i class="ph-chat-circle-bold"></i>
        <h2>算法备注</h2>
      </div>
  
      <div class="comment-container">
        <!-- 评论列表
        <div class="comment-list" ref="commentList">
          <div 
            v-for="comment in comments" 
            :key="comment.id" 
            class="comment-item"
          >
            <div class="comment-info">
              <span class="username">{{ comment.username }}</span>
              <span class="time">{{ comment.time }}</span>
            </div>
            <p class="comment-content">{{ comment.content }}</p>
          </div>
        </div> -->
  
        <!-- 评论输入框 -->
        <div class="comment-input-wrapper">
          <textarea
            v-model="newComment"
            class="comment-input"
            placeholder="请输入算法备注..."
            :rows="1"
            @input="autoGrow"
            ref="textarea"
          ></textarea>
          <!-- <button 
            class="send-button"
            :disabled="!newComment.trim()"
            @click="submitComment"
          >
            <i class="ph-paper-plane-right-bold"></i>
          </button> -->
        </div>
      </div>
    </div>
  </template>
  
  <script>
  export default {
    name: 'CommentSection',
  
    data() {
      return {
        newComment: '',
        comments: [
          {
            id: 1,
            username: '用户1',
            time: '10:30',
            content: '这个算法看起来不错'
          },
          {
            id: 2,
            username: '用户2',
            time: '11:45',
            content: '运行效果很好'
          }
        ]
      }
    },
  
    methods: {
      autoGrow() {
        const textarea = this.$refs.textarea
        textarea.style.height = 'auto'
        textarea.style.height = textarea.scrollHeight + 'px'
      },
  
      submitComment() {
        if (!this.newComment.trim()) return
  
        const newCommentObj = {
          id: this.comments.length + 1,
          username: '当前用户',
          time: new Date().toLocaleTimeString('zh-CN', { 
            hour: '2-digit', 
            minute: '2-digit' 
          }),
          content: this.newComment.trim()
        }
  
        this.comments.push(newCommentObj)
        this.newComment = ''
        this.$nextTick(() => {
          this.autoGrow()
          this.scrollToBottom()
        })
      },
  
      scrollToBottom() {
        const commentList = this.$refs.commentList
        commentList.scrollTop = commentList.scrollHeight
      }
    },
  
    mounted() {
      this.scrollToBottom()
    }
  }
  </script>
  
  <style scoped>
  .comment-section {
    background-color: white;
    padding: 1.5rem;
    border-radius: 8px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  }
  
  .comment-header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 1rem;
  }
  
  .comment-header i {
    font-size: 1.5rem;
    color: #666;
  }
  
  .comment-header h2 {
    font-size: 1.2rem;
    font-weight: 600;
    color: #333;
    margin: 0;
  }
  
  .comment-container {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }
  
  .comment-list {
    max-height: 200px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    padding-right: 0.5rem;
  }
  
  .comment-item {
    background-color: #f5f5f5;
    padding: 1rem;
    border-radius: 8px;
  }
  
  .comment-info {
    display: flex;
    justify-content: space-between;
    margin-bottom: 0.5rem;
  }
  
  .username {
    font-weight: 500;
    color: #333;
  }
  
  .time {
    color: #666;
    font-size: 0.9rem;
  }
  
  .comment-content {
    margin: 0;
    color: #444;
    line-height: 1.4;
  }
  
  .comment-input-wrapper {
    display: flex;
    gap: 0.5rem;
    align-items: flex-end;
  }
  
  .comment-input {
    flex: 1;
    padding: 0.75rem;
    border: 1px solid #ddd;
    border-radius: 4px;
    resize: none;
    min-height: 20px;
    max-height: 120px;
    line-height: 1.4;
    font-family: inherit;
  }
  
  .comment-input:focus {
    outline: none;
    border-color: #2196f3;
  }
  
  .send-button {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0.75rem;
    background-color: #2196f3;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    transition: background-color 0.3s ease;
  }
  
  .send-button:hover:not(:disabled) {
    background-color: #1976d2;
  }
  
  .send-button:disabled {
    background-color: #ccc;
    cursor: not-allowed;
  }
  
  .send-button i {
    font-size: 1.2rem;
  }
  
  /* 自定义滚动条样式 */
  .comment-list::-webkit-scrollbar {
    width: 6px;
  }
  
  .comment-list::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 3px;
  }
  
  .comment-list::-webkit-scrollbar-thumb {
    background: #ccc;
    border-radius: 3px;
  }
  
  .comment-list::-webkit-scrollbar-thumb:hover {
    background: #999;
  }
  
  @media (max-width: 768px) {
    .comment-section {
      padding: 1rem;
    }
  
    .comment-list {
      max-height: 150px;
    }
  }
  </style>