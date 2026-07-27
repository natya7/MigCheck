UPDATE "public"."users" t SET "note" = p."note" FROM "public"."migcheck_backup_users_note" p WHERE t."id" = p."id";
DROP TABLE "public"."migcheck_backup_users_note";
