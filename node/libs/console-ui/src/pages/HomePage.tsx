import {PageContainer} from "@ant-design/pro-components";
import {useKubernetesJWTTokenQuery} from "../apis/token";

export default () => {
  useKubernetesJWTTokenQuery(undefined)
  return <PageContainer title={"综合门户"}>
    <p>显示很多东西</p>
  </PageContainer>
}
