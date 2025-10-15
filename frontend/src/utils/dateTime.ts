/*
 * @Author: Rhymedys/Rhymedys@gmail.com
 * @Date: 2018-09-28 22:52:17
 * @Last Modified by: Hoyung
 * @Last Modified time: 2022-04-18 17:02:57
 */

import dayjs, { isDayjs, Dayjs } from "dayjs";

export const dateMapFormatValueMap = {
  date: "YYYY-MM-DD",
  datetime: "YYYY-MM-DD HH:mm:ss",
  monthrange: "YYYY-MM",
  year: "YYYY"
};

/**
 * @description 格式化moment对象
 * @export
 * @param {*} obj
 * @returns
 */

export function formatMomentObj2YYYYMMDDHHMMSS(obj: Dayjs) {
  let res = "";
  if (!obj) {
    return res;
  }
  let momentObj = obj;

  let isMomentoObj = isDayjs(obj);

  if (!isMomentoObj) {
    try {
      momentObj = dayjs(obj);
      isMomentoObj = true;
    } catch (e) {
      console.warn("formatMomentObj2YYYYMMDDHHMMSS error", e);
    }
  }

  if (isMomentoObj) {
    res = dayjs(momentObj).format("YYYY-MM-DD HH:mm:ss");
  }

  return res;
}

/**
 *
 *
 * @export
 * @param {Dayjs} obj
 * @returns
 */
export function formatMomentObj2StartYYYYMMDDHHMMSS(
  obj: Dayjs,
  autoGenerate?: boolean,
  startOf?: boolean
) {
  let res = "";
  if (!obj) {
    return res;
  }
  let momentObj = obj;

  let isMomentoObj = isDayjs(obj);

  if (!isMomentoObj && autoGenerate) {
    try {
      momentObj = dayjs(obj);
      isMomentoObj = true;
    } catch (e) {
      console.warn("formatMomentObj2StartYYYYMMDDHHMMSS error", e);
    }
  }

  if (isMomentoObj) {
    let timeStamp;

    if (startOf) {
      timeStamp = dayjs(momentObj).clone();
    } else {
      timeStamp = dayjs(momentObj).clone().startOf("d");
    }

    res = timeStamp.format("YYYY-MM-DD HH:mm:ss");
  }

  return res;
}

/**
 *
 *
 * @export
 * @param {Dayjs} obj
 * @returns
 */
export function formatMomentObj2EndYYYYMMDDHHMMSS(
  obj: Dayjs,
  autoGenerate?: boolean,
  endOf?: boolean
) {
  let res = "";
  if (!obj) {
    return res;
  }
  let momentObj = obj;

  let isMomentoObj = isDayjs(obj);

  if (!isMomentoObj && autoGenerate) {
    try {
      momentObj = dayjs(obj);
      isMomentoObj = true;
    } catch (e) {
      console.warn("formatMomentObj2EndYYYYMMDDHHMMSS error", e);
    }
  }

  if (isMomentoObj) {
    let timeStamp;

    if (endOf) {
      timeStamp = dayjs(momentObj).clone();
    } else {
      timeStamp = dayjs(momentObj).clone().endOf("d");
    }

    res = timeStamp.format("YYYY-MM-DD HH:mm:ss");
  }

  return res;
}

/**
 *
 *
 * @export
 * @param {Dayjs} obj
 * @returns
 */
export function formatMomentObJ2YYYYMMDD(obj: Dayjs) {
  let res = "";
  if (!obj) {
    return res;
  }
  let momentObj = obj;

  let isMomentoObj = isDayjs(obj);

  if (!isMomentoObj) {
    try {
      momentObj = dayjs(obj);
      isMomentoObj = true;
    } catch (e) {
      console.warn("formatMomentObJ2YYYYMMDD error", e);
    }
  }

  if (isMomentoObj) {
    res = momentObj.format("YYYY-MM-DD");
  }

  return res;
}

/**
 *
 *
 * @export
 * @param {Dayjs} obj
 * @returns
 */
export function formatMomentObJ2HHMMSS(obj: Dayjs) {
  let res = "";
  if (!obj) {
    return res;
  }
  let momentObj = obj;

  let isMomentoObj = isDayjs(obj);

  if (!isMomentoObj) {
    try {
      momentObj = dayjs(obj);
      isMomentoObj = true;
    } catch (e) {
      console.warn("formatMomentObJ2HHMMSS error", e);
    }
  }

  if (isMomentoObj) {
    res = momentObj.format("HH:mm:ss");
  }

  return res;
}

export function formatMomentObJ2HHMM(obj: Dayjs) {
  let res = "";
  if (!obj) {
    return res;
  }
  let momentObj = obj;

  let isMomentoObj = isDayjs(obj);

  if (!isMomentoObj) {
    try {
      momentObj = dayjs(obj);
      isMomentoObj = true;
    } catch (e) {
      console.warn("formatMomentObJ2HHMMSS error", e);
    }
  }

  if (isMomentoObj) {
    res = momentObj.format("HH:mm");
  }

  return res;
}
export function formatMomentObJ2YYYYMMDDHHMM(obj: Dayjs) {
  let res = "";
  if (!obj) {
    return res;
  }
  let momentObj = obj;

  let isMomentoObj = isDayjs(obj);

  if (!isMomentoObj) {
    try {
      momentObj = dayjs(obj);
      isMomentoObj = true;
    } catch (e) {
      console.warn("formatMomentObJ2YYYYMMDDHHMM error", e);
    }
  }

  if (isMomentoObj) {
    res = momentObj.format("YYYY-MM-DD HH:mm");
  }

  return res;
}

// 秒转化为 m分n秒
export function getTimeRender(text?: number | null) {
  let minute;
  let seconds;
  if (text) {
    if (text <= 60 && text > 0) {
      return text + "秒";
    }
    if (text > 60) {
      minute = parseInt((text / 60).toString(), 10);
      seconds = text - minute * 60;
      return minute + "分钟" + seconds + "秒";
    }
  }
  return "——";
}
