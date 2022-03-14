This directory provides two ways to start Stargate with Cassandra 4.0 using docker-compose.

# Stargate with 3-node Cassandra 4.0 cluster

Use the shell script `start_cass_40.sh`, which performs an orderly startup of containers 
specified in `docker-compose.yml`:

`./start_cass_40.sh`

This script exits when all containers have started or a failure is detected. You can remove
the stack of containers created by executing `docker compose down`.

# Stargate with embedded Cassandra 4.0 (developer mode) 

This alternate configuration that runs the Stargate coordinator node in developer mode, so that no
separate Cassandra cluster is required. This can be run with the command:

`./start_cass_40_dev_mode.sh`

You can stop execution of this script with `Ctrl-C` and the stack will be torn down.

# Notes

* The `.env` file defines variables for the docker compose project name (`COMPOSE_PROJECT_NAME`),
  and the DSE Docker image tag to use (`DSETAG`).

* The Stargate Docker image tag to use (`SGTAG`) defaults to the current version as defined in the
  top level project `pom.xml` file. It can be overridden with the `-t` option on either script:

  `./start_cass_40.sh -t v2.0.0-ALPHA-2`

* Running more than one of these multi-container environments on one host may require
changing the port mapping to be changed to avoid conflicts on the host machine.