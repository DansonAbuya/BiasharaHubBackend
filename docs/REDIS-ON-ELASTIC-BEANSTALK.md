# Setting up Redis on the Elastic Beanstalk environment

The backend uses Redis for Spring Cache. To run it on EB you need a Redis instance (e.g. **Amazon ElastiCache for Redis**) in the same network as your EB environment, then set the connection details in EB environment properties.

---

## 1. Create Redis (Amazon ElastiCache)

1. In **AWS Console** go to **ElastiCache** → **Redis** (or **Redis OSS**).
2. Click **Create Redis cache**.
3. Choose:
   - **Cluster mode**: disabled (unless you need clustering).
   - **Name**: e.g. `biasharahub-uat-redis`.
   - **Engine version**: 7.x or 6.x (recommended).
   - **Node type**: e.g. `cache.t3.micro` for UAT, larger for prod.
   - **Number of replicas**: 0 for UAT (single node), 1+ for HA if needed.
4. **Network** (important):
   - **VPC**: use the **same VPC** as your Elastic Beanstalk environment (and RDS).
   - **Subnets**: pick subnets that your EB instances can reach (often the same as EB or in the same VPC).
   - **Security group**: create a new one or use an existing one; you will allow **inbound TCP 6379** from the EB security group in the next step.
5. **Security group** (after creation if needed):
   - Open **EC2** → **Security Groups**.
   - Find the security group attached to your ElastiCache cluster.
   - **Edit inbound rules** → Add rule: **Type** = Custom TCP, **Port** = 6379, **Source** = security group of your **EB environment** (so only EB instances can connect). Save.
6. Create the cluster and wait until status is **Available**.

---

## 2. Get Redis endpoint

1. In **ElastiCache** → **Redis** → open your cluster.
2. Copy the **Primary endpoint** (hostname), e.g. `biasharahub-uat-redis.xxxxx.0001.euw1.cache.amazonaws.com`.
3. Port is **6379** unless you changed it.

---

## 3. Configure Elastic Beanstalk to use Redis

1. Go to **Elastic Beanstalk** → your environment (e.g. **biasharahub-uat**).
2. **Configuration** → **Software** → **Edit**.
3. Under **Environment properties** add:

| Name          | Value                                              |
|---------------|----------------------------------------------------|
| `REDIS_HOST`  | Primary endpoint from step 2 (hostname only, no port) |
| `REDIS_PORT`  | `6379`                                             |

4. **Apply**.

The app reads these via `application.yml` (`spring.data.redis.host` / `port`) and uses Redis for cache.

---

## 4. Checklist

- [ ] ElastiCache cluster is in the **same VPC** as the EB environment (and RDS if in same VPC).
- [ ] Redis security group allows **inbound TCP 6379** from the **EB environment’s security group** (not 0.0.0.0/0).
- [ ] EB **Environment properties** include `REDIS_HOST` and `REDIS_PORT`.
- [ ] After saving config, EB will redeploy; check app logs if the app fails to start (e.g. connection refused = security group or VPC/subnet issue).

---

## 5. Jenkins Test stage (optional)

If you run `mvn test` in Jenkins and want tests to use the same Redis, set the same `REDIS_HOST` and `REDIS_PORT` in the Jenkins job’s environment (and ensure the Jenkins agent can reach the Redis endpoint, e.g. same VPC or VPN). See **JENKINS-TEST-STAGE.md** for DB and Redis env vars.

---

## Summary

- Use **ElastiCache for Redis** in the **same VPC** as EB.
- Allow **port 6379** from the **EB security group** to the Redis security group.
- Set **REDIS_HOST** (primary endpoint hostname) and **REDIS_PORT** (6379) in EB **Configuration → Software → Environment properties**.
