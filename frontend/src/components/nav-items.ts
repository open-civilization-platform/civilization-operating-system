import {
  BarChart3, Beaker, Gamepad2, Handshake, Heart, ArrowLeftRight,
  Network, ScrollText, Trees, Trophy, Warehouse, Globe,
} from 'lucide-react'

export const navItems = [
  { path: '/', label: 'Dashboard', icon: BarChart3 },
  { path: '/civilizations', label: 'Civilizations', icon: Globe },
  { path: '/nexus', label: 'Nexus Mesh', icon: Network },
  { path: '/resources', label: 'Resources', icon: Trees },
  { path: '/production', label: 'Production', icon: Warehouse },
  { path: '/logistics', label: 'Logistics', icon: Handshake },
  { path: '/trade', label: 'Trade', icon: ArrowLeftRight },
  { path: '/constitution', label: 'Governance', icon: ScrollText },
  { path: '/social', label: 'Social', icon: Heart },
  { path: '/tech-tree', label: 'Technology', icon: Beaker },
  { path: '/simulation', label: 'Simulation', icon: Gamepad2 },
  { path: '/leaderboard', label: 'Leaderboard', icon: Trophy },
]
