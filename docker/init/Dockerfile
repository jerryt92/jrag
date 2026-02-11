FROM debian:bookworm-slim AS init
COPY jrag-starter/src/main/resources/application.yaml /templates/application.yaml
COPY jrag-starter/src/main/resources/application.yaml /templates/application-mysql.yaml
COPY jrag-starter/src/main/resources/application.yaml /templates/application-sqlite.yaml
COPY jrag-starter/src/main/resources/application.yaml /templates/application-product.yaml
COPY jrag-starter/src/main/resources/application.yaml /templates/qa-template.json
COPY jrag-starter/src/main/resources/application.yaml /templates/system_prompt.txt
COPY docker/init/init-config.sh /init/init-config.sh

