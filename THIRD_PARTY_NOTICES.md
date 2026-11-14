# Third-party notices

Chiyuri Desktop includes the following archive libraries and supporting components:

- **Apache Commons Compress 1.28.0** — 7z archive reading. Licensed under Apache License 2.0; see [licenses/APACHE-2.0.txt](licenses/APACHE-2.0.txt). Upstream: https://commons.apache.org/proper/commons-compress/
- **Zip4j 2.11.6** — ZIP listing and extraction, including AES and traditional password-protected ZIPs. Licensed under Apache License 2.0; see [licenses/APACHE-2.0.txt](licenses/APACHE-2.0.txt). Upstream: https://github.com/srikanth-lingala/zip4j
- **XZ for Java 1.12** — optional 7z codec support. Licensed under 0BSD; see [licenses/XZ-0BSD-LICENSE.txt](licenses/XZ-0BSD-LICENSE.txt). Upstream: https://tukaani.org/xz/java.html
- **Junrar 8.1.2** — RAR archive reading/extraction only. Distributed under the UnRAR license; see [licenses/UNRAR-LICENSE.txt](licenses/UNRAR-LICENSE.txt). Upstream: https://github.com/junrar/junrar
- **SLF4J API** — Junrar logging dependency, MIT License; see [licenses/SLF4J-MIT-LICENSE.txt](licenses/SLF4J-MIT-LICENSE.txt).

Junrar is used only to read and extract RAR archives. This project does not create or write RAR archives and does not implement a RAR-compatible archiver. The UnRAR license restriction must remain visible in the source and documentation.

Archive extraction is limited to 1,000 entries and 256 MiB of expanded output per operation. ZIP, 7z and RAR source files/volume sets are limited to 512 MiB; folder copy/move is limited to 1,000 entries, 64 levels and 512 MiB. The 7z reader uses a 64 MiB memory cap for codecs that honor it; RAR dictionary memory is capped at 128 MiB.

Archive extraction preserves safe relative folder paths. Absolute paths, drive-prefixed paths, . and .. segments, control characters, paths deeper than 64 levels and segments longer than 255 characters are rejected to prevent traversal. Duplicate output names are skipped.

ZIP AES and traditional encryption are supported; encrypted RAR/7Z archives require the password field. ZIP strong encryption is not supported. RAR5 multipart sets named .part1.rar, .part2.rar and so on, and RAR4 old-style .rar/.r00/.r01 sets are supported when every part is present. RAR 1.4 multi-volume extraction remains outside the current Junrar implementation. Unsupported codecs may fail with an explanatory message.