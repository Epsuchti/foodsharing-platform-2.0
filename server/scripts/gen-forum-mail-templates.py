#!/usr/bin/env python3
"""Generates the forum notification mail fragments (FORUM_POST, FORUM_THREAD_TO_ACTIVATE) in five languages.

Writes src/main/resources/templates/mail/notification/<TYPE>_<lang>.{html,txt}. Subjects live in
src/main/resources/i18n/mail/messages_<lang>.properties. Re-run after editing: python3 server/scripts/gen-forum-mail-templates.py
"""
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "templates",
                    "mail", "notification")

T = {
    "de": ("Hallo", "Öffnen", "Dein foodsharing-Team",
           "hat im Forum auf «{t}» geantwortet:",
           "hat das Thema «{t}» eröffnet. Es wartet auf deine Freischaltung."),
    "en": ("Hello", "Open", "Your foodsharing team",
           "replied in the forum thread \"{t}\":",
           "started the thread \"{t}\". It is waiting for your approval."),
    "gws": ("Hoi", "Öffne", "Dis foodsharing-Team",
            "het im Forum uf «{t}» gantwortet:",
            "het s Thema «{t}» eröffnet. Es wartet uf dini Freischaltig."),
    "fr": ("Bonjour", "Ouvrir", "Ton équipe foodsharing",
           "a répondu dans le sujet du forum « {t} » :",
           "a ouvert le sujet « {t} ». Il attend ton approbation."),
    "it": ("Ciao", "Apri", "Il tuo team foodsharing",
           "ha risposto nella discussione del forum «{t}»:",
           "ha aperto la discussione «{t}». È in attesa della tua approvazione."),
}
BTN = ('style="display:inline-block;padding:10px 18px;background:#25834a;color:#ffffff;text-decoration:none;'
       'border-radius:6px;font-weight:bold;"')
BOX = 'style="margin:0 0 12px;padding:12px;background:#f4f6f4;border-radius:6px;white-space:pre-line;"'

for lang, (hello, open_, team, post, act) in T.items():
    for typ, sentence, with_body in (("FORUM_POST", post, True), ("FORUM_THREAD_TO_ACTIVATE", act, False)):
        pre, post_ = sentence.split("{t}")
        html = ["<p style=\"margin:0 0 12px;\" th:text=\"'%s ' + ${displayName} + ','\">%s,</p>" % (hello, hello),
                "<p style=\"margin:0 0 12px;\"><strong th:text=\"${author}\">author</strong> %s"
                "<span th:text=\"${title}\">title</span>%s</p>" % (pre, post_)]
        txt = ["%s [(${displayName})]," % hello, "", "[(${author})] %s[(${title})]%s" % (pre, post_)]
        if with_body:
            html.append("<p th:if=\"${body != null}\" %s th:text=\"${body}\">body</p>" % BOX)
            txt += ["[# th:if=\"${body != null}\"]", "[(${body})]", "[/]"]
        html.append("<p th:if=\"${link != null}\" style=\"margin:0 0 20px;\"><a th:href=\"${link}\" href=\"#\" %s>%s</a></p>"
                    % (BTN, open_))
        html.append("<p style=\"margin:0 0 12px;\">%s</p>" % team)
        txt += ["[# th:if=\"${link != null}\"]", "[(${link})]", "[/]", team]
        with open(os.path.join(ROOT, "%s_%s.html" % (typ, lang)), "w", encoding="utf-8") as fh:
            fh.write("\n".join(html) + "\n")
        with open(os.path.join(ROOT, "%s_%s.txt" % (typ, lang)), "w", encoding="utf-8") as fh:
            fh.write("\n".join(txt) + "\n")
