import { useEffect, useRef, useState } from 'react';

export function useTabPanelActive(initialActive = true) {
  const anchorRef = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(initialActive);

  useEffect(() => {
    const panel = anchorRef.current?.closest<HTMLElement>('[role="tabpanel"]');
    if (!panel) {
      setActive(true);
      return;
    }

    const update = () => {
      setActive(
        panel.getAttribute('aria-hidden') !== 'true' &&
          !panel.classList.contains('ant-tabs-tabpane-hidden'),
      );
    };
    update();
    const observer = new MutationObserver(update);
    observer.observe(panel, {
      attributes: true,
      attributeFilter: ['aria-hidden', 'class'],
    });
    return () => observer.disconnect();
  }, []);

  return { anchorRef, active };
}
