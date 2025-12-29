import { sm4 } from "sm-crypto";

// 工具函数：Base64 解码为 Uint8Array
function base64ToUint8Array(base64) {
  const binaryString = atob(base64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes;
}

// 工具函数：Uint8Array 转 Hex 字符串
function uint8ArrayToHex(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

// 工具函数：Hex 字符串转 UTF-8 字符串
function hexToUtf8(hex) {
  const bytes = new Uint8Array(
    hex.match(/.{1,2}/g).map((byte) => parseInt(byte, 16))
  );
  return new TextDecoder("utf-8").decode(bytes);
}

export function sm4Decrypt(keyBase64, cipherText, options) {
  try {
    // 1. 解析密钥（16字节）
    const keyBytes = base64ToUint8Array(keyBase64);
    // if (keyBytes.length !== 16) {
    //   throw new Error("密钥长度必须为16字节");
    // }
    const keyHex = uint8ArrayToHex(keyBytes);

    // 2. 解析密文
    const cipherBytes = base64ToUint8Array(cipherText);
    const cipherHex = uint8ArrayToHex(cipherBytes);

    // 3. 调用 sm4 解密（ECB + PKCS7）
    const decryptedHex = sm4.decrypt(
      cipherHex,
      keyHex
      //   options || {
      //     mode: "ecb",
      //     padding: "pkcs7",
      //   }
    );

    // 4. 转为文本
    // const plainText = hexToUtf8(decryptedHex);

    return decryptedHex;
  } catch (err) {
    console.error("❌ 解密失败", err);
  }
}

window.sm4Decrypt = sm4Decrypt;
