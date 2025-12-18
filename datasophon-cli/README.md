```shell
java -jar datasophon-cli-2.0-SNAPSHOT-cli.jar init httpd -c cluster-sample.yml -p /data/shell/pd/scripts-set/unit-scripts/httpd-install/httpd-pkg -rp /data/shell/httpd -port 4081 -d /data/shell/pd/scripts-set/unit-scripts/templates -h httpd.conf.ftl -f
java -jar datasophon-cli-2.0-SNAPSHOT-cli.jar init yumconf -c cluster-sample.yml -ip 192.168.2.43 -port 4080
```