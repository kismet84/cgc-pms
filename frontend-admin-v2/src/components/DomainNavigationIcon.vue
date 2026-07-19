<script setup lang="ts">
defineProps<{ domainId: string }>()
</script>

<template>
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <template v-if="domainId === 'workbench'">
      <rect x="3" y="4" width="18" height="16" rx="2.5" stroke="currentColor" stroke-width="1.8" />
      <path d="M3 8H21" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      <path
        d="M7 16V13M12 16V10M17 16V12"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      />
    </template>

    <template v-else-if="domainId === 'delivery'">
      <rect x="5" y="4" width="14" height="17" rx="2.5" stroke="currentColor" stroke-width="1.8" />
      <path
        d="M9 4.5V3.8C9 3.36 9.36 3 9.8 3H14.2C14.64 3 15 3.36 15 3.8V4.5"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      />
      <path
        d="M8 10L9.5 11.5L12 8.5"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <path
        d="M14 10H16.5M8 16L9.5 17.5L12 14.5M14 16H16.5"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>

    <template v-else-if="domainId === 'commercial'">
      <path
        d="M6 3.5H14L19 8.5V20.5H6V3.5Z"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linejoin="round"
      />
      <path d="M14 3.5V8.5H19" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
      <path
        d="M8.5 12H16.5M8.5 15H14"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      />
      <path
        d="M9 19L10.8 17.2C11.46 16.54 12.54 16.54 13.2 17.2L15 19"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </template>

    <template v-else-if="domainId === 'supply'">
      <path
        d="M4 7.5L12 3L20 7.5L12 12L4 7.5Z"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linejoin="round"
      />
      <path
        d="M4 7.5V16.5L12 21V12"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linejoin="round"
      />
      <path
        d="M20 7.5V13M8 5.2L16 9.8"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      />
      <path
        d="M17.5 16.5L19 15C19.83 14.17 21.17 14.17 22 15C22.83 15.83 22.83 17.17 22 18L20.5 19.5"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      />
      <path
        d="M18.5 20.5L17 22C16.17 22.83 14.83 22.83 14 22C13.17 21.17 13.17 19.83 14 19L15.5 17.5"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      />
    </template>

    <template v-else-if="domainId === 'subcontract-settlement'">
      <path
        d="M3.5 7L10 3.5L16.5 7L10 10.5L3.5 7Z"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linejoin="round"
      />
      <path
        d="M3.5 11L10 14.5L15 11.8M3.5 15L10 18.5L14 16.3"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <rect x="15" y="12" width="6" height="9" rx="1.5" stroke="currentColor" stroke-width="1.8" />
      <path
        d="M17 15H19M17 18H17.1M19 18H19.1"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      />
    </template>

    <template v-else-if="domainId === 'finance'">
      <path
        d="M4 6.5C4 5.12 5.12 4 6.5 4H17.5C18.88 4 20 5.12 20 6.5V18C20 19.1 19.1 20 18 20H6C4.9 20 4 19.1 4 18V6.5Z"
        stroke="currentColor"
        stroke-width="1.8"
      />
      <path
        d="M4 8H18.5C19.88 8 21 9.12 21 10.5V14.5C21 15.88 19.88 17 18.5 17H16"
        stroke="currentColor"
        stroke-width="1.8"
      />
      <circle cx="17.5" cy="12.5" r="1" fill="currentColor" />
      <path
        d="M9 10L12 13M12 10L9 13M10.5 13V17M8.5 15H12.5"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
      />
    </template>

    <template v-else-if="domainId === 'master-data'">
      <path
        d="M3.5 6.5C3.5 5.4 4.4 4.5 5.5 4.5H10L12 6.5H18.5C19.6 6.5 20.5 7.4 20.5 8.5V18.5C20.5 19.6 19.6 20.5 18.5 20.5H5.5C4.4 20.5 3.5 19.6 3.5 18.5V6.5Z"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linejoin="round"
      />
      <ellipse cx="16.5" cy="12" rx="3" ry="1.5" stroke="currentColor" stroke-width="1.6" />
      <path
        d="M13.5 12V17C13.5 17.83 14.84 18.5 16.5 18.5C18.16 18.5 19.5 17.83 19.5 17V12"
        stroke="currentColor"
        stroke-width="1.6"
      />
      <path
        d="M13.5 14.5C13.5 15.33 14.84 16 16.5 16C18.16 16 19.5 15.33 19.5 14.5"
        stroke="currentColor"
        stroke-width="1.6"
      />
    </template>

    <template v-else-if="domainId === 'system-management'">
      <path
        d="M9.75 3H14.25L14.75 5.1C15.3 5.28 15.82 5.5 16.31 5.78L18.15 4.65L21.35 7.85L20.22 9.69C20.5 10.18 20.72 10.7 20.9 11.25L23 11.75V16.25L20.9 16.75C20.72 17.3 20.5 17.82 20.22 18.31L21.35 20.15L18.15 23.35L16.31 22.22C15.82 22.5 15.3 22.72 14.75 22.9L14.25 25H9.75L9.25 22.9C8.7 22.72 8.18 22.5 7.69 22.22L5.85 23.35L2.65 20.15L3.78 18.31C3.5 17.82 3.28 17.3 3.1 16.75L1 16.25V11.75L3.1 11.25C3.28 10.7 3.5 10.18 3.78 9.69L2.65 7.85L5.85 4.65L7.69 5.78C8.18 5.5 8.7 5.28 9.25 5.1L9.75 3Z"
        transform="scale(.82) translate(2.6 0)"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linejoin="round"
      />
      <circle cx="12" cy="11.5" r="3.2" stroke="currentColor" stroke-width="1.8" />
      <path
        d="M17 17H22M18 20H22M19 14H22"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      />
    </template>
  </svg>
</template>
