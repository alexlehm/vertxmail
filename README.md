# Vert.x smtp client

Please note that this project is mostly obsolete since it was migrated to vert.x 3 and continued in the vert-x project
at https://github.com/vert-x3/vertx-mail-service

A very preliminary version of a smtp client for vert.x.

Currently this is working with Vert.x 2.1.*, its missing a few bits to be
really useful most importantly it has no real vert.x api yet. The main
verticle shows how this could be used, the whole client is async and
supports SSL, STARTTLS, SASL).

For now the source is available at https://github.com/alexlehm/vertxmail

For feedback either send me a mail or drop by on the vert.x channel irc://irc.freenode.net/#vertx

