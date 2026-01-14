import { ServiceConfigData } from '../../apis/service';

export interface ServiceDeployment {
  service: ServiceConfigData;
  envId: string;
}
