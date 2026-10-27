import type { DevApi } from './api';
import type { CapturedMail } from './types';

export interface WaitForMailOptions {
  /** Recipient (case-insensitive exact match). */
  to: string;
  /** Substring of the subject (case-insensitive). */
  subjectContains?: string;
  /** Extra filter, e.g. on the text body. */
  predicate?: (mail: CapturedMail) => boolean;
  /** Only mails sent at/after this ISO instant (server clock). */
  sentAfter?: string;
  /** Default 15 s. */
  timeoutMs?: number;
}

/**
 * Mails captured by the e2e profile's capturing transport (`GET /api/dev/mails`). The outbox job sends
 * asynchronously, so always use {@link waitForMail} instead of reading the list once.
 */
export class MailBox {
  constructor(private readonly dev: DevApi) {}

  list(to?: string): Promise<CapturedMail[]> {
    return this.dev.mails(to);
  }

  clear(): Promise<void> {
    return this.dev.clearMails();
  }

  async waitForMail(options: WaitForMailOptions): Promise<CapturedMail> {
    const deadline = Date.now() + (options.timeoutMs ?? 15_000);
    const to = options.to.toLowerCase();
    let seen: CapturedMail[] = [];
    while (Date.now() < deadline) {
      seen = await this.dev.mails(options.to);
      const match = seen.find(
        (mail) =>
          mail.to.toLowerCase() === to &&
          (!options.subjectContains || mail.subject.toLowerCase().includes(options.subjectContains.toLowerCase())) &&
          (!options.sentAfter || mail.sentAt >= options.sentAfter) &&
          (!options.predicate || options.predicate(mail)),
      );
      if (match) return match;
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
    throw new Error(
      `No mail to ${options.to}${options.subjectContains ? ` with subject containing "${options.subjectContains}"` : ''}; ` +
        `captured for this recipient: ${JSON.stringify(seen.map((mail) => mail.subject))}`,
    );
  }

  /**
   * First link of the mail matching `match` (substring or RegExp; default: any link). Returns the absolute URL
   * exactly as sent (host = the server's app.base-url).
   */
  extractLink(mail: CapturedMail, match?: string | RegExp): string {
    const links = mail.links.length ? mail.links : linksFromText(`${mail.text}\n${mail.html ?? ''}`);
    const link = links.find((candidate) =>
      match === undefined ? true : typeof match === 'string' ? candidate.includes(match) : match.test(candidate),
    );
    if (!link) throw new Error(`Mail "${mail.subject}" has no link matching ${String(match)}: ${links.join(', ')}`);
    return link;
  }

  /**
   * Like {@link extractLink} but returns only path + query + hash, so `page.goto()` resolves it against the test's
   * baseURL (needed when the app is reached under another host, e.g. host.docker.internal in the screenshot image).
   */
  extractPath(mail: CapturedMail, match?: string | RegExp): string {
    const url = new URL(this.extractLink(mail, match));
    return `${url.pathname}${url.search}${url.hash}`;
  }
}

function linksFromText(text: string): string[] {
  return [...text.matchAll(/https?:\/\/[^\s"'<>]+/g)].map((match) => match[0].replace(/&amp;/g, '&'));
}
