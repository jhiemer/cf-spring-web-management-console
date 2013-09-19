package com.github.kratos.service

import groovyx.net.http.HTTPBuilder
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value

class CloudFoundryService implements InitializingBean {

    @Value('${kratos.cloudfoundry.api.url}')
    def baseApiUrl

    def api

    @Override
    void afterPropertiesSet() throws Exception {
        api = new HTTPBuilder(baseApiUrl)
    }

    def organizations(token) {
        def organizations = api.get(path: '/v2/organizations',
                headers: [authorization: token, accept:'application/json'],
                query: ['inline-relations-depth' : '0'])
        def result = []
        for(organization in organizations.resources){
            result.add([id:organization.metadata.guid, name:organization.entity.name, quotaId:organization.entity.quota_definition_guid])
        }
        return result
    }

    def organization(token, id) {
        def cfOrganization = api.get(path: '/v2/organizations/'.concat(id),
                headers: [authorization: token, accept:'application/json'],
                query: ['inline-relations-depth' : '4'])
        def organization = [id:cfOrganization.metadata.guid, name: cfOrganization.entity.name, quotaId:  cfOrganization.entity.quota_definition_guid, users:[], spaces:[]]
        for(cfSpace in cfOrganization.entity.spaces){
            def space = [id:cfSpace.metadata.guid, name: cfSpace.entity.name, apps:[]]
            for(cfApp in cfSpace.entity.apps){
                space.apps.add([id:cfApp.metadata.guid, name:cfApp.entity.name])
            }
            organization.spaces.add(space)
        }
        for(cfUser in cfOrganization.entity.users){
            organization.users.add([id:cfUser.metadata.guid])
        }
        return organization
    }

    def applications(token) {
        def response = api.get(path: '/v2/apps', headers: [authorization: token])

        def applications = []
        for (application in response.resources) {
            applications.add([id: application.metadata.guid, name: application.entity.name])
        }
        applications
    }

    def application(token, id) {
        def response = api.get(path: '/v2/apps/'.concat(id), headers: [authorization: token], query: ['inline-relations-depth': '3'])

        def buildpack = response.entity.buildpack ? response.entity.buildpack : response.entity.detected.buildpack

        def urls = []
        for (route in response.entity.routes) {
            def host = route.entity.host
            def domain = route.entity.domain.entity.name
            urls.add(host.concat('.').concat(domain))
        }

        def serviceBindings = []
        for (binding in response.entity.service_bindings) {
            def plan = binding.entity.service_instance.entity.service_plan
            def servicePlan = [id: plan.metadata.guid, name: plan.entity.name, description: plan.entity.description]

            def instance = binding.entity.service_instance
            def serviceInstance = [id: instance.metadata.guid, name: instance.entity.name, servicePlan: servicePlan]

            serviceBindings.add([id: binding.metadata.guid, serviceInstance: serviceInstance])
        }

        [id: response.metadata.guid, name: response.entity.name, memory: response.entity.memory, diskQuota: response.entity.disk_quota,
                state: response.entity.state, buildpack: buildpack, urls: urls, serviceBindings: serviceBindings]
    }

}
