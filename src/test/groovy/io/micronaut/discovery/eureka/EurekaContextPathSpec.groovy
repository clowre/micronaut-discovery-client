package io.micronaut.discovery.eureka

import io.micronaut.context.ApplicationContext
import io.micronaut.core.naming.NameUtils
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.discovery.eureka.client.v2.InstanceInfo
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class EurekaContextPathSpec extends Specification {


    void "test that the server reports a heartbeat to Eureka"() {

        given:
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, [
                'eureka.client.context-path'           : '/eureka/v2',
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                (MockEurekaServer.ENABLED)             : true
        ])

        when: "An application is started and eureka configured"
        String serviceId = 'heartbeatService'
        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                ['consul.client.enabled'                    : false,
                 'eureka.client.context-path'               : '/eureka/v2',
                 'eureka.client.host'                       : eurekaServer.getHost(),
                 'eureka.client.port'                       : eurekaServer.getPort(),
                 'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                 'micronaut.application.name'               : serviceId,
                 'micronaut.heartbeat.interval'             : '1s']
        )

        DiscoveryClient discoveryClient = application.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5)

        then: "The application is registered"
        conditions.eventually {
            Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst().size() == 1
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].values().first()
            instanceInfo.status == InstanceInfo.Status.UP
            // heart beat received
            MockEurekaServer.heartbeats[NameUtils.hyphenate(serviceId)].values().first()
        }

        when: "The application is stopped"
        application?.stop()

        then: "The application is de-registered"
        conditions.eventually {
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 0
        }

        cleanup:
        eurekaServer?.stop()
    }

}
