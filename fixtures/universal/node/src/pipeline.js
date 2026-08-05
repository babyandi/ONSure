'use strict';

function render(request) {
  if (!request || typeof request.title !== 'string' || !request.title.trim()) {
    throw new Error('TITLE_REQUIRED');
  }
  return Buffer.from(JSON.stringify({ title: request.title.trim(), exposed: false }), 'utf8');
}

module.exports = { render };
