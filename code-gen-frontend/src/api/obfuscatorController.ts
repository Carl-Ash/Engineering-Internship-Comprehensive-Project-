// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** GET /obfuscator/schemes */
export async function getObfuscatorSchemes(options?: { [key: string]: any }) {
  return request<any>('/obfuscator/schemes', {
    method: 'GET',
    ...(options || {}),
  })
}

/** POST /obfuscator/obfuscate */
export async function obfuscateCode(
  body: API.ObfuscateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseObfuscateVO>('/obfuscator/obfuscate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
