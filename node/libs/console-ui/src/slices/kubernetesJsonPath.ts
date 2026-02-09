import _ from 'lodash';

/**
 * 支持从 object 中读取;条件必须是 类似 name="main"
 * @param obj 当前对象
 * @param field ".spec.template.spec.containers[name=\"main\"].restartPolicy"
 */
export function readKubernetesField(obj: any, field: string) {
  // 都只能识别一个
  const dot = field.indexOf('.', 1);
  const nextField = dot == -1 ? field : field.substring(0, dot);
  const next = nextKubernetesPathValue(obj, nextField);
  if (!next) return undefined;
  if (dot == -1) return next;
  return readKubernetesField(next, field.substring(dot));
}

/**
 * 即将修改 obj !!!
 * @param obj 被修改的对象!!!
 * @param field 一样的规则
 * @param value 目标值
 */
export function updateKubernetesField(obj: any, field: string, value: any) {
  const dot = field.indexOf('.', 1);
  if (dot == -1) {
    // 最终处理
    updateKubernetesFieldValue(obj, field, value);
  } else {
    // 获取
    const nextField = field.substring(0, dot);
    const next = requiredNextKubernetesPathValue(obj, nextField);
    updateKubernetesField(next, field.substring(dot), value);
  }
}

/**
 * 即将修改 obj !!!
 * @param obj 被修改的对象!!!
 * @param field 一样的规则
 */
export function removeKubernetesField(obj: any, field: string) {
  const dot = field.indexOf('.', 1);
  if (dot == -1) {
    // 最终处理
    removeKubernetesFieldValue(obj, field);
  } else {
    // 获取
    const nextField = field.substring(0, dot);
    const next = nextKubernetesPathValue(obj, nextField);
    if (!next) throw `意图移除${obj}的${field}时,其${nextField}的结果不可靠`;
    removeKubernetesField(next, field.substring(dot));
  }
}

const arrayPathRegex = /^(.+)\[(.+)]$/;

function nextKubernetesPathValue(obj: any, inputField: string) {
  if (!inputField.startsWith('.')) throw `path:${inputField} 应该是.开头的`;
  const field = inputField.substring(1);
  const ap = field.match(arrayPathRegex);
  if (ap != null) {
    const ary = obj[ap[1]];
    if (ary === null || ary === undefined) {
      console.warn(`${obj} 使用的${ap[1]}为空`);
      return undefined;
    }
    const condition = ap[2].replace('=', '==');

    if (!_.isArrayLike(ary)) {
      throw `使用 path:${inputField}, 可以获取到的中间结果却不是数组,而是:${ary}`;
    }
    const afterFilter = ary.filter((it: any) => {
      const fn = new Function('c', `return c.${condition}`);
      return fn(it);
    });
    if (afterFilter.length == 0) {
      console.warn(`${obj} 使用 过滤器:${condition}后结果为 0`);
      return undefined;
    }
    return afterFilter[0];
  } else return obj[field];
}

// 与 nextKubernetesPathValue 不同，它必须返回有效结果，没有就创建，也就是 obj是会变的
function requiredNextKubernetesPathValue(obj: any, inputField: string) {
  if (!inputField.startsWith('.')) throw `path:${inputField} 应该是.开头的`;
  const field = inputField.substring(1);
  const ap = field.match(arrayPathRegex);
  if (ap != null) {
    const condition = ap[2].replace('=', '==');
    const ary = createArrayIfNesseary(obj, ap[1]);
    const afterFilter = ary.filter((it: any) => {
      const fn = new Function('c', `return c.${condition}`);
      return fn(it);
    });
    if (afterFilter.length == 0) {
      const newEle = {};
      const fn = new Function(
        'c',
        `c.${condition.replace('===', '==').replace('==', '=')}`
      );
      fn(newEle);
      ary.push(newEle);
      return newEle;
    }
    return afterFilter[0];
  } else {
    if (obj[field]) {
      return obj[field];
    }
    obj[field] = {};
    return obj[field];
  }
}

function removeKubernetesFieldValue(obj: any, inputField: string) {
  if (!inputField.startsWith('.')) throw `path:${inputField} 应该是.开头的`;
  const field = inputField.substring(1);
  const ap = field.match(arrayPathRegex);
  if (ap != null) {
    const condition = ap[2].replace('=', '==');
    const ary = obj[ap[1]];
    if (!_.isArrayLike(ary)) {
      throw `使用 path:${inputField}, 可以获取到的中间结果却不是数组,而是:${ary}`;
    }
    const afterFilter = ary.findIndex((it: any) => {
      const fn = new Function('c', `return c.${condition}`);
      return fn(it);
    });
    if (afterFilter == -1) {
      throw `使用 过滤器:${condition}后结果为 0`;
    }
    ary.splice(afterFilter, 1);
  } else {
    delete obj[field];
  }
}

function createArrayIfNesseary(obj: any, fieldName: string) {
  const inputAry = obj[fieldName];
  if (inputAry === null || inputAry === undefined) {
    obj[fieldName] = [];
  }
  const ary = obj[fieldName];
  if (!_.isArrayLike(ary)) {
    throw `使用 path:${fieldName}, 可以获取到的中间结果却不是数组,而是:${ary}`;
  }
  return ary;
}

function updateKubernetesFieldValue(obj: any, inputField: string, value: any) {
  if (!inputField.startsWith('.')) throw `path:${inputField} 应该是.开头的`;
  const field = inputField.substring(1);
  const ap = field.match(arrayPathRegex);
  if (ap != null) {
    const condition = ap[2].replace('=', '==');
    const ary = createArrayIfNesseary(obj, ap[1]);
    const afterFilter = ary.findIndex((it: any) => {
      const fn = new Function('c', `return c.${condition}`);
      return fn(it);
    });
    if (afterFilter == -1) {
      // 没有对象直接添加
      const newEle = { ...value };
      const fn = new Function(
        'c',
        `c.${condition.replace('===', '==').replace('==', '=')}`
      );
      fn(newEle);
      ary.push(newEle);
      return;
    }
    _.assign(ary[afterFilter], value);
  } else {
    obj[field] = value;
  }
}
