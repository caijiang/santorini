import { IIngress } from 'kubernetes-models/networking.k8s.io/v1';

type Rules = NonNullable<NonNullable<IIngress['spec']>['rules']>;
type HttpPath = NonNullable<Rules[number]['http']>['paths'][number];

/**
 * ingress 其中一个 path
 */
export interface IngressPath {
  instance: IIngress;
  ruleIndex: number;
  schema: 'http';
  pathIndex: number;
  path: HttpPath;
}

export function ingressPathKey(input: IngressPath) {
  return (
    input.instance.metadata?.name +
    ':' +
    input.ruleIndex +
    ':' +
    input.schema +
    ':' +
    input.pathIndex
  );
}

export function toHttpPaths(input: IIngress): IngressPath[] {
  return (
    input.spec?.rules
      ?.flatMap((rule, ruleIndex) =>
        rule?.http?.paths?.flatMap((path, pathIndex) => ({
          instance: input,
          ruleIndex,
          schema: 'http' as 'http',
          pathIndex,
          path,
        }))
      )
      ?.filter((it) => !!it)
      ?.map((it) => it!!) ?? []
  );
}
