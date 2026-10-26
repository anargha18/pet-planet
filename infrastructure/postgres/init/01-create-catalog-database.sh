#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=catalog_database_name="$CATALOG_DATABASE_NAME" \
  --set=catalog_database_username="$CATALOG_DATABASE_USERNAME" \
  --set=catalog_database_password="$CATALOG_DATABASE_PASSWORD" \
  --set=order_database_name="$ORDER_DATABASE_NAME" \
  --set=order_database_username="$ORDER_DATABASE_USERNAME" \
  --set=order_database_password="$ORDER_DATABASE_PASSWORD" \
  --set=inventory_database_name="$INVENTORY_DATABASE_NAME" \
  --set=inventory_database_username="$INVENTORY_DATABASE_USERNAME" \
  --set=inventory_database_password="$INVENTORY_DATABASE_PASSWORD" \
  --set=payment_database_name="$PAYMENT_DATABASE_NAME" \
  --set=payment_database_username="$PAYMENT_DATABASE_USERNAME" \
  --set=payment_database_password="$PAYMENT_DATABASE_PASSWORD" \
  --set=notification_database_name="$NOTIFICATION_DATABASE_NAME" \
  --set=notification_database_username="$NOTIFICATION_DATABASE_USERNAME" \
  --set=notification_database_password="$NOTIFICATION_DATABASE_PASSWORD" \
  --set=shipping_database_name="$SHIPPING_DATABASE_NAME" \
  --set=shipping_database_username="$SHIPPING_DATABASE_USERNAME" \
  --set=shipping_database_password="$SHIPPING_DATABASE_PASSWORD" \
  --set=delivery_database_name="$DELIVERY_DATABASE_NAME" \
  --set=delivery_database_username="$DELIVERY_DATABASE_USERNAME" \
  --set=delivery_database_password="$DELIVERY_DATABASE_PASSWORD" \
  --set=fulfillment_database_name="$FULFILLMENT_DATABASE_NAME" \
  --set=fulfillment_database_username="$FULFILLMENT_DATABASE_USERNAME" \
  --set=fulfillment_database_password="$FULFILLMENT_DATABASE_PASSWORD" \
  --set=user_database_name="$USER_DATABASE_NAME" \
  --set=user_database_username="$USER_DATABASE_USERNAME" \
  --set=user_database_password="$USER_DATABASE_PASSWORD" <<'EOSQL'
CREATE USER :"catalog_database_username" WITH PASSWORD :'catalog_database_password';
CREATE DATABASE :"catalog_database_name" OWNER :"catalog_database_username";

CREATE USER :"order_database_username" WITH PASSWORD :'order_database_password';
CREATE DATABASE :"order_database_name" OWNER :"order_database_username";

CREATE USER :"inventory_database_username" WITH PASSWORD :'inventory_database_password';
CREATE DATABASE :"inventory_database_name" OWNER :"inventory_database_username";

CREATE USER :"payment_database_username" WITH PASSWORD :'payment_database_password';
CREATE DATABASE :"payment_database_name" OWNER :"payment_database_username";

CREATE USER :"notification_database_username" WITH PASSWORD :'notification_database_password';
CREATE DATABASE :"notification_database_name" OWNER :"notification_database_username";

CREATE USER :"shipping_database_username" WITH PASSWORD :'shipping_database_password';
CREATE DATABASE :"shipping_database_name" OWNER :"shipping_database_username";

CREATE USER :"delivery_database_username" WITH PASSWORD :'delivery_database_password';
CREATE DATABASE :"delivery_database_name" OWNER :"delivery_database_username";

CREATE USER :"fulfillment_database_username" WITH PASSWORD :'fulfillment_database_password';
CREATE DATABASE :"fulfillment_database_name" OWNER :"fulfillment_database_username";

CREATE USER :"user_database_username" WITH PASSWORD :'user_database_password';
CREATE DATABASE :"user_database_name" OWNER :"user_database_username";
EOSQL
