import Vue from 'vue'
import Vuex from 'vuex'
import app from './modules/app'
import dict from './modules/dict'
import user from './modules/user'
import tagsView from './modules/tagsView'
import permission from './modules/permission'
import settings from './modules/settings'
import getters from './getters'

Vue.use(Vuex)

const store = new Vuex.Store({
  state:{
    algorithm:"yolo"
  },
  modules: {
    app,
    dict,
    user,
    tagsView,
    permission,
    settings
  },
  mutations: {
    changeName(state,algo) {
    state.algorithm = algo;
    }
    },
  getters
})

export default store
