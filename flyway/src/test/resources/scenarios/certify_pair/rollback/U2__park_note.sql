CREATE TABLE "public"."migcheck_backup_users_note" ("id" bigint, "note" character varying(50), PRIMARY KEY ("id"));
INSERT INTO "public"."migcheck_backup_users_note" SELECT "id", "note" FROM "public"."users";
ALTER TABLE users DROP COLUMN note;
