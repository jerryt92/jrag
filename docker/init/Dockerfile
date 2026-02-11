FROM debian:bookworm-slim AS init
COPY jrag-starter/src/main/resources/application.yaml /templates/application.yaml
COPY jrag-starter/src/main/resources/application-mysql.yaml /templates/application-mysql.yaml
COPY jrag-starter/src/main/resources/application-sqlite.yaml /templates/application-sqlite.yaml
COPY jrag-starter/src/main/resources/application-product.yaml /templates/application-product.yaml
COPY jrag-starter/src/main/resources/qa-template.json /templates/qa-template.json
COPY jrag-starter/src/main/resources/system_prompt.txt /templates/system_prompt.txt
COPY docker/init/init-config.sh /init/init-config.sh

