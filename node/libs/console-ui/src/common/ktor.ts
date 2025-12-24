import type { ProTableProps } from '@ant-design/pro-table/es/typing';
import { baseAxios } from '@private-everest/app-support';
import _ from 'lodash';
import { ProSchemaValueEnumType } from '@ant-design/pro-provider';

export interface PageResult<T> {
  records: T[];
  total: number;
}

// 这里是开发 ktor 服务端的几个简单约定
export function toKtorRequest<
  DataType = Record<string, any>,
  ParamType = Record<string, string>
>(
  requestUri: string,
  customParams?: (input: ParamType) => any
): ProTableProps<DataType, ParamType>['request'] {
  // , sorter, filter
  const uri = '/api' + requestUri;
  return async (params) => {
    const { pageSize, current, keyword, ...otherParams } = params;
    const moreParams =
      otherParams && _.keys(otherParams).length > 0
        ? customParams
          ? customParams(otherParams as ParamType)
          : otherParams
        : undefined;
    // 如果没有请求分页
    if (pageSize != undefined && current != undefined) {
      const rs = await baseAxios<PageResult<DataType>>(uri, {
        params: {
          limit: pageSize,
          offset: pageSize * (current - 1),
          ...moreParams,
        },
      }).then((it) => it.data);
      const { records, total } = rs;
      console.debug(
        'origin result:',
        rs,
        'total:',
        total,
        ',records:',
        records
      );
      return {
        success: true,
        data: records,
        total,
      };
    } else {
      const rs = await baseAxios<DataType[]>(uri, {
        params: moreParams,
      }).then((it) => it.data);
      return {
        success: true,
        data: rs,
      };
    }
  };
}

export function toInnaNameRule(number: number = 15) {
  return {
    pattern: RegExp(`^[a-z]([a-z0-9-]{0,${number - 1}})$`),
    message: `非法 IANA Name（必须是 1–${number} 位小写字母、数字或 -，且字母开头）`,
  };
}

/**
 * //    有效标签值：
 * //    必须为 63 个字符或更少（可以为空）
 * //    除非标签值为空，必须以字母数字字符（[a-z0-9A-Z]）开头和结尾
 * //    包含破折号（-）、下划线（_）、点（.）和字母或数字
 */
export function toLabelValueRule() {
  return {
    pattern: RegExp(`^[a-z0-9A-Z]([a-z0-9A-Z-_.]{0,61})[a-z0-9A-Z]$`),
    message: `必须为 63 个字符或更少，必须以字母数字字符开头和结尾，只包含破折号（-）、下划线（_）、点（.）和字母或数字`,
  };
}

/**
 * 将 kotlin 原生枚举处理成  ProSchemaValueEnumType
 * @param input 枚举数组
 * @param toEnum 可选的额外处理函数
 */
export function toProSchemaValueEnumMap<
  T extends {
    name: string;
  }
>(
  input: Array<T>,
  toEnum: (input: T) => ProSchemaValueEnumType | undefined = (it) => ({
    text: it.name,
  })
) {
  return arrayToProSchemaValueEnumMap((it) => it.name, input, toEnum);
}

/**
 * 将 任意 array  ProSchemaValueEnumType
 * @param toName 处理成名字
 * @param input 数组
 * @param toEnum 可选的额外处理函数
 */
export function arrayToProSchemaValueEnumMap<T>(
  toName: (input: T) => string,
  input: Array<T> | T[],
  toEnum: (input: T) => ProSchemaValueEnumType | undefined = (it) => ({
    text: toName(it),
  })
) {
  return _.transform(
    input,
    (obj, it) => {
      const x = toEnum(it);
      if (x) {
        obj[toName(it)] = x;
      }
    },
    {} as Record<string, ProSchemaValueEnumType>
  );
}
