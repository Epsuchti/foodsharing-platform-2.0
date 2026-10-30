/** At most this many images per content page (`ContentPageUpdate.imageUploadIds` maxItems). */
export const MAX_IMAGES = 20;

/** Page images are claimed CONTENT_IMAGE and public (A41): the body links the large variant. */
export function contentImageUrls(uploadId: string): { url: string; thumb: string } {
  return {
    url: `/api/uploads/${uploadId}?size=lg`,
    thumb: `/api/uploads/${uploadId}?size=sm`,
  };
}

/** Markdown of an image in a page body (alt text left to the author). */
export function imageMarkdown(url: string, alt = ''): string {
  return `![${alt.replace(/[[\]]/g, '')}](${url})`;
}
