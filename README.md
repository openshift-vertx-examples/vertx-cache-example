# vertx-cache-booster

This booster demonstrates how to use a cache server from an Eclipse Vert.x application.

## Deployment

1. Be sure to be logged in to your OpenShift cluster, and you are in the right OpenShift project
2. Deploy the cache server using:
```bash
oc apply -f service.cache.yml
```
3. Deploy the booster with:
```bash
mvn fabric8:deploy -Popenshift
```
4. Access the exposed route (`greeting-service`) - a HTML page to use the booster is exposed.

## Integration tests

Run integrations test using:

```bash
mvn verify -Popenshift,openshift-it
```
