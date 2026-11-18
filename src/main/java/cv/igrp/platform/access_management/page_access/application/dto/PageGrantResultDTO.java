package cv.igrp.platform.access_management.page_access.application.dto;

import java.util.List;

/**
 * The page granted and every key the role holds for it now.
 *
 * @param page    the page key
 * @param granted the required keys and the chosen optional ones
 */
public record PageGrantResultDTO(String page, List<String> granted) {
}
