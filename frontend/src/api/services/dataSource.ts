import { METHOD, request } from '../../utils/request'
import {GOODS, GOODS_COLUMNS} from './api'

export async function goodsList(params) {
  return request(GOODS, METHOD.GET, params)
}

export async function goodsColumns() {
  return request(GOODS_COLUMNS, METHOD.GET)
}

export default {goodsList, goodsColumns}